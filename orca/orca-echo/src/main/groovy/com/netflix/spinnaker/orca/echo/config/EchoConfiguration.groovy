/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingExecutionListener
import com.netflix.spinnaker.orca.echo.spring.EchoNotifyingStageListener
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter
import com.netflix.spinnaker.orca.events.StageListenerAdapter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import


@Configuration
@Import([RetrofitConfiguration])
@ConditionalOnExpression('${echo.enabled:true}')
@ComponentScan("com.netflix.spinnaker.orca.echo")
@CompileStatic
class EchoConfiguration {

  @Autowired OkHttpClientProvider clientProvider
  @Autowired ObjectMapper objectMapper

  @Bean
  EchoService echoService(@Value('${echo.base-url}') String echoBaseUrl, ServiceClientProvider serviceClientProvider) {
    serviceClientProvider.getService(
        EchoService,
        new DefaultServiceEndpoint("echo", RetrofitUtils.getBaseUrl(echoBaseUrl)))
  }

  @Bean
  EchoNotifyingStageListener echoNotifyingStageExecutionListener(EchoService echoService, ExecutionRepository repository,
                                                                 ContextParameterProcessor contextParameterProcessor,
                                                                 DynamicConfigService dynamicConfigService) {
    new EchoNotifyingStageListener(echoService, contextParameterProcessor, dynamicConfigService)
  }

  @Bean
  ApplicationListener<ExecutionEvent> echoNotifyingStageExecutionListenerAdapter(EchoNotifyingStageListener echoNotifyingStageListener) {
    return new StageListenerAdapter(echoNotifyingStageListener)
  }

  @Bean
  EchoNotifyingExecutionListener echoNotifyingPipelineExecutionListener(
    EchoService echoService,
    Front50Service front50Service,
    ObjectMapper objectMapper,
    ContextParameterProcessor contextParameterProcessor) {
    new EchoNotifyingExecutionListener(
      echoService,
      front50Service,
      objectMapper,
      contextParameterProcessor
    )
  }

  @Bean
  ApplicationListener<ExecutionEvent> echoNotifyingPipelineExecutionListenerAdapter(EchoNotifyingExecutionListener echoNotifyingExecutionListener, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(echoNotifyingExecutionListener, repository)
  }
}
