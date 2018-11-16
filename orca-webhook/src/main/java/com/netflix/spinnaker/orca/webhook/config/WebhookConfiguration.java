/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "webhook.stage", value = "enabled", matchIfMissing = true)
@ComponentScan("com.netflix.spinnaker.orca.webhook")
@EnableConfigurationProperties(PreconfiguredWebhookProperties.class)
public class WebhookConfiguration {
  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate(ClientHttpRequestFactory webhookRequestFactory) {
    RestTemplate restTemplate = new RestTemplate(webhookRequestFactory);

    List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
    converters.add(new ObjectStringHttpMessageConverter());
    restTemplate.setMessageConverters(converters);

    return restTemplate;
  }

  @Bean
  public ClientHttpRequestFactory webhookRequestFactory() {
    X509TrustManager trustManager = getTrustManager(null);
    SSLSocketFactory sslSocketFactory = getSSLSocketFactory(trustManager);
    OkHttpClient client = new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, trustManager).build();
    return new OkHttp3ClientHttpRequestFactory(client);
  }

  private SSLSocketFactory getSSLSocketFactory(X509TrustManager trustManager) {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new X509TrustManager[]{trustManager}, null);
      return sslContext.getSocketFactory();
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private X509TrustManager getTrustManager(KeyStore keyStore) {
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      return (X509TrustManager) trustManagers[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public class ObjectStringHttpMessageConverter extends StringHttpMessageConverter {
    @Override
    public boolean supports(Class<?> clazz) {
      return clazz == Object.class;
    }
  }
}
