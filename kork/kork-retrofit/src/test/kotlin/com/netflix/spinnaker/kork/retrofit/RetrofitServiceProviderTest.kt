/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.kork.retrofit

import brave.Tracing
import brave.http.HttpTracing
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.RetrofitConfiguration
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory
import com.netflix.spinnaker.config.DefaultServiceClientProvider
import com.netflix.spinnaker.config.OkHttpClientComponents
import com.netflix.spinnaker.kork.client.ServiceClientFactory
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Callback
import retrofit.http.GET
import retrofit.http.Path
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class RetrofitServiceProviderTest  : JUnit5Minutests {

  fun tests() = rootContext {
    derivedContext<ApplicationContextRunner>("no configuration") {
      fixture {
        ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(
            RetrofitServiceFactoryAutoConfiguration::class.java,
            TaskExecutionAutoConfiguration::class.java,
            DefaultOkHttpClientBuilderProvider::class.java,
            OkHttpClientComponents::class.java,
            RetrofitConfiguration::class.java,
            TestConfiguration::class.java
          ))
      }

      test("initializes service client provider") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(ServiceClientProvider::class.java)).get { size }.isEqualTo(1)
            that(ctx.getBean(ServiceClientProvider::class.java).getService(Retrofit1Service::class.java, DefaultServiceEndpoint("retrofit1", "https://www.test.com"), ServiceClientProvider.RetrofitVersion.RETROFIT1)).isA<Retrofit1Service>()
          }
        }
      }

      test("initializes service client factories") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(ctx.getBeansOfType(ServiceClientFactory::class.java)).get { size }.isEqualTo(1)
          }
        }
      }

      test("check if Retrofit2EncodeCorrectionInterceptor is added to OkHttpClientProvider") {
        run { ctx: AssertableApplicationContext ->
          expect {
            that(
              ctx.getBean(OkHttpClientProvider::class.java)
                .getClient(DefaultServiceEndpoint("retrofit1", "https://www.test.com"))
                .interceptors
                .count { it is Retrofit2EncodeCorrectionInterceptor }
            ).isEqualTo(0)
          }
        }
      }

    }
  }

}

@Configuration
private open class TestConfiguration {

  @Bean
  open fun httpTracing(): HttpTracing =
    HttpTracing.newBuilder(Tracing.newBuilder().build()).build()

  @Bean
  open fun okHttpClient(httpTracing: HttpTracing): OkHttpClient {
    return RawOkHttpClientFactory().create(OkHttpClientConfigurationProperties(), emptyList(), httpTracing)
  }

  @Bean
  open fun objectMapper(): ObjectMapper {
    return  ObjectMapper()
  }

  @Bean
  open fun serviceClientProvider(
    serviceClientFactories: List<ServiceClientFactory?>, objectMapper: ObjectMapper): DefaultServiceClientProvider {
    return DefaultServiceClientProvider(serviceClientFactories, objectMapper)
  }

}

interface Retrofit1Service {

  @GET("/users/{user}/something")
  fun getSomething(@Path("user") user: String?, callback: Callback<List<*>?>?)

}
