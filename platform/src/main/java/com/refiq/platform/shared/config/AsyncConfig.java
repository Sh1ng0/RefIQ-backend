package com.refiq.platform.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Configuration
public class AsyncConfig {


  @Bean(name = "ingestionExecutor")
  public ExecutorService ingestionExecutor() {
    // Spring gestionará el ciclo de vida y llamará a close() en el shutdown
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean(name = "webhookExecutor")
  public ExecutorService webhookExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}


