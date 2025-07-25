import { capitalize, get, isEmpty, map } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type {
  IAccountDetails,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  IManifest,
} from '@spinnaker/core';
import {
  ArtifactTypePatterns,
  CheckboxInput,
  RadioButtonInput,
  SETTINGS,
  StageArtifactSelectorDelegate,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import { CopyFromTemplateButton } from './CopyFromTemplateButton';
import type { IManifestBindArtifact } from './ManifestBindArtifactsSelector';
import { ManifestBindArtifactsSelector } from './ManifestBindArtifactsSelector';
import { ManifestDeploymentOptions } from './ManifestDeploymentOptions';
import { NamespaceSelector } from './NamespaceSelector';
import { ManifestSource } from '../../../manifest/ManifestSource';
import type { IManifestLabelSelector } from '../../../manifest/selector/IManifestLabelSelector';
import type { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import LabelEditor from '../../../manifest/selector/labelEditor/LabelEditor';
import { ManifestBasicSettings } from '../../../manifest/wizard/BasicSettings';
import { fetchManifests } from './manifestStatus/utils/fetchManifests';

interface IDeployManifestStageConfigFormProps {
  accounts: IAccountDetails[];
  selector?: IManifestSelector;
  modes?: SelectorMode.Label;
}

interface IDeployManifestStageConfigFormState {
  rawManifest: string;
  overrideNamespace: boolean;
  selector: IManifestSelector;
  labelSelectors: IManifestLabelSelector[];
}

export class DeployManifestStageForm extends React.Component<
  IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps,
  IDeployManifestStageConfigFormState
> {
  private readonly excludedManifestArtifactTypes = [
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ArtifactTypePatterns.MAVEN_FILE,
  ];

  public constructor(props: IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
    super(props);
    this.state = {
      rawManifest: '',
      overrideNamespace: false,
      selector: {
        account: '',
        location: '',
        mode: SelectorMode.Label,
        labelSelectors: { selectors: [] },
      },
      labelSelectors: [],
    };
  }

  public componentDidMount(): void {
    const stage = this.props.formik.values;
    const rawManifests: any[] = get(this.props.formik.values, 'manifests');
    const isTextManifest: boolean = get(this.props.formik.values, 'source') === ManifestSource.TEXT;
    fetchManifests(rawManifests)
      .then((manifests: any[]) => {
        this.setState({
          rawManifest: !isEmpty(manifests) && isTextManifest ? yamlDocumentsToString(manifests) : '',
        });
      })
      .catch((error) => {
        console.error('Failed to fetch manifests:', error);
      })
      .finally(() => {
        this.setState({
          overrideNamespace: get(stage, 'namespaceOverride', '') !== '',
        });
      });
  }

  private getSourceOptions = (): Array<Option<string>> => {
    return map([ManifestSource.TEXT, ManifestSource.ARTIFACT], (option) => ({
      label: capitalize(option),
      value: option,
    }));
  };

  private handleCopy = (manifest: IManifest): void => {
    this.props.formik.setFieldValue('manifests', [manifest]);
    this.setState({
      rawManifest: yamlDocumentsToString([manifest]),
    });
  };

  private handleRawManifestChange = (rawManifest: string, manifests: any): void => {
    this.setState({
      rawManifest,
    });
    this.props.formik.setFieldValue('manifests', manifests);
  };

  private onManifestArtifactSelected = (expectedArtifactId: string): void => {
    this.props.formik.setFieldValue('manifestArtifactId', expectedArtifactId);
    this.props.formik.setFieldValue('manifestArtifact', null);
  };

  private onManifestArtifactEdited = (artifact: IArtifact) => {
    this.props.formik.setFieldValue('manifestArtifactId', null);
    this.props.formik.setFieldValue('manifestArtifact', artifact);
  };

  private getRequiredArtifacts = (): IManifestBindArtifact[] => {
    const { requiredArtifactIds, requiredArtifacts } = this.props.formik.values;
    return (requiredArtifactIds || [])
      .map((id: string) => ({ expectedArtifactId: id }))
      .concat(requiredArtifacts || []);
  };

  private onRequiredArtifactsChanged = (bindings: IManifestBindArtifact[]): void => {
    this.props.formik.setFieldValue(
      'requiredArtifactIds',
      bindings.filter((b) => b.expectedArtifactId).map((b) => b.expectedArtifactId),
    );
    this.props.formik.setFieldValue(
      'requiredArtifacts',
      bindings.filter((b) => b.artifact),
    );
  };

  private overrideNamespaceChange(checked: boolean) {
    if (!checked) {
      this.props.formik.setFieldValue('namespaceOverride', '');
    }
    this.setState({ overrideNamespace: checked });
  }

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="form-horizontal">
        <h4>Basic Settings</h4>
        <ManifestBasicSettings
          accounts={this.props.accounts}
          onAccountSelect={(accountName) => this.props.formik.setFieldValue('account', accountName)}
          selectedAccount={stage.account}
        />
        <StageConfigField label="Override Namespace">
          <CheckboxInput
            checked={this.state.overrideNamespace}
            onChange={(e: any) => this.overrideNamespaceChange(e.target.checked)}
          />
        </StageConfigField>
        {this.state.overrideNamespace && (
          <StageConfigField label="Namespace">
            <NamespaceSelector
              createable={true}
              accounts={this.props.accounts}
              selectedAccount={stage.account}
              selectedNamespace={stage.namespaceOverride || ''}
              onChange={(namespace) => this.props.formik.setFieldValue('namespaceOverride', namespace)}
            />
          </StageConfigField>
        )}
        <hr />
        <h4>Manifest Configuration</h4>
        <StageConfigField label="Manifest Source" helpKey="kubernetes.manifest.source">
          <RadioButtonInput
            options={this.getSourceOptions()}
            onChange={(e: any) => this.props.formik.setFieldValue('source', e.target.value)}
            value={stage.source}
          />
        </StageConfigField>
        {stage.source === ManifestSource.TEXT && (
          <StageConfigField label="Manifest">
            <CopyFromTemplateButton application={this.props.application} handleCopy={this.handleCopy} />
            <YamlEditor onChange={this.handleRawManifestChange} value={this.state.rawManifest} />
          </StageConfigField>
        )}
        {stage.source === ManifestSource.ARTIFACT && (
          <>
            <StageArtifactSelectorDelegate
              artifact={stage.manifestArtifact}
              excludedArtifactTypePatterns={this.excludedManifestArtifactTypes}
              expectedArtifactId={stage.manifestArtifactId}
              helpKey="kubernetes.manifest.expectedArtifact"
              label="Manifest Artifact"
              onArtifactEdited={this.onManifestArtifactEdited}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onManifestArtifactSelected(artifact.id)}
              pipeline={this.props.pipeline}
              stage={stage}
            />
            <StageConfigField label="Expression Evaluation" helpKey="kubernetes.manifest.skipExpressionEvaluation">
              <CheckboxInput
                checked={stage.skipExpressionEvaluation === true}
                onChange={(e: any) => this.props.formik.setFieldValue('skipExpressionEvaluation', e.target.checked)}
                text="Skip SpEL expression evaluation"
              />
            </StageConfigField>
          </>
        )}
        <StageConfigField label="Required Artifacts to Bind" helpKey="kubernetes.manifest.requiredArtifactsToBind">
          <ManifestBindArtifactsSelector
            bindings={this.getRequiredArtifacts()}
            onChangeBindings={this.onRequiredArtifactsChanged}
            pipeline={this.props.pipeline}
            stage={stage}
          />
        </StageConfigField>
        {SETTINGS.feature.deployManifestStageAdvancedConfiguration && (
          <>
            <hr />
            <h4>Deploy Configuration</h4>
            <StageConfigField label="Skip Spec Template Labels" helpKey="kubernetes.manifest.skipSpecTemplateLabels">
              <CheckboxInput
                checked={stage.skipSpecTemplateLabels === true}
                onChange={(e: any) => this.props.formik.setFieldValue('skipSpecTemplateLabels', e.target.checked)}
              />
            </StageConfigField>
            <StageConfigField label="Label Selectors" helpKey="kubernetes.manifest.deployLabelSelectors">
              <CheckboxInput
                checked={stage.labelSelectors != null}
                onChange={(e: any) => {
                  if (e.target.checked) {
                    this.props.formik.setFieldValue('labelSelectors', { selectors: [] });
                    this.props.formik.setFieldValue('allowNothingSelected', false);
                  } else {
                    this.props.formik.setFieldValue('labelSelectors', null);
                    this.props.formik.setFieldValue('allowNothingSelected', null);
                  }
                }}
              />
            </StageConfigField>
            {stage.labelSelectors && stage.labelSelectors.selectors && (
              <>
                <StageConfigField label="Labels">
                  <LabelEditor
                    labelSelectors={this.props.formik.values.labelSelectors.selectors}
                    onLabelSelectorsChange={this.handleLabelSelectorsChange}
                  />
                </StageConfigField>
                <StageConfigField
                  label="Allow nothing selected"
                  helpKey="kubernetes.manifest.deployLabelSelectors.allowNothingSelected"
                >
                  <CheckboxInput
                    checked={stage.allowNothingSelected === true}
                    onChange={(e: any) => this.props.formik.setFieldValue('allowNothingSelected', e.target.checked)}
                  />
                </StageConfigField>
              </>
            )}
          </>
        )}
        <hr />
        <ManifestDeploymentOptions
          accounts={this.props.accounts}
          config={stage.trafficManagement}
          onConfigChange={(config) => this.props.formik.setFieldValue('trafficManagement', config)}
          selectedAccount={stage.account}
        />
      </div>
    );
  }

  private handleLabelSelectorsChange = (labelSelectors: IManifestLabelSelector[]): void => {
    this.setState({ labelSelectors });
    this.props.formik.setFieldValue('labelSelectors.selectors', labelSelectors);
  };
}
