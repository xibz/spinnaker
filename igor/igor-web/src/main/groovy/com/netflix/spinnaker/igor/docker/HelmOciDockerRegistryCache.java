/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.docker;

import static java.lang.String.format;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Cache for Helm OCI Docker Registry data. */
@Service
@Qualifier("HelmOciDockerRegistryCache")
public class HelmOciDockerRegistryCache extends DockerRegistryCache {

  static final String ID = "helmOciDockerRegistry";

  // docker-digest must conform to hash:hashvalue. The string "~" explicitly avoids this to act as
  // an "empty" placeholder.
  private static final String EMPTY_DIGEST = "~";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public HelmOciDockerRegistryCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    super(redisClientDelegate, igorConfigurationProperties);
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  @Override
  public Set<String> getImages(String account) {
    Set<String> result = new HashSet<>();
    redisClientDelegate.withKeyScan(
        makeHelmOciIndexPattern(prefix(), account),
        1000,
        page -> {
          result.addAll(page.getResults());
        });
    return result;
  }

  @Override
  public String getLastDigest(String account, String repository, String tag) {
    String key = new DockerRegistryV2Key(prefix(), ID, account, repository, tag).toString();
    return redisClientDelegate.withCommandsClient(
        c -> {
          Map<String, String> res = c.hgetAll(key);
          if (res.isEmpty()
              || res.get("digest") == null
              || res.get("digest").equals(EMPTY_DIGEST)) {
            return null;
          }
          return res.get("digest");
        });
  }

  @Override
  public void setLastDigest(String account, String repository, String tag, String digest) {
    String key = new DockerRegistryV2Key(prefix(), ID, account, repository, tag).toString();
    String d = digest == null ? EMPTY_DIGEST : digest;
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "digest", d);
        });
  }

  private String makeHelmOciIndexPattern(String prefix, String account) {
    return format("%s:%s:v2:%s:*", prefix, ID, account);
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}
