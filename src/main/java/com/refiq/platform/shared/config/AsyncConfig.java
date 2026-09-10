package com.refiq.platform.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configures thread pools for asynchronous operations across the platform.
 * <p>
 * Leverages Java Virtual Threads (Project Loom) to handle high-concurrency,
 * I/O-bound tasks (such as S3 streaming and webhook dispatching) with minimal
 * OS-level thread overhead.
 * </p>
 */
@Configuration
public class AsyncConfig {

  @Bean(name = "ingestionExecutor")
  public ExecutorService ingestionExecutor() {
    // Spring manages the lifecycle and automatically calls close() during graceful shutdown
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean(name = "webhookExecutor")
  public ExecutorService webhookExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}