package com.refiq.platform.calculation.internal.adapter.plumber;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PlumberClientConfig {

  @Bean
  public RestClient plumberRestClient(
      RestClient.Builder builder,
      @Value("${plumber.api.url}") String baseUrl,
      @Value("${plumber.timeout.read-seconds:60}") int readTimeoutSeconds) {

    // Configuramos la red dura (Timeouts)
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(readTimeoutSeconds * 1000);

    return builder
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .build();
  }
}