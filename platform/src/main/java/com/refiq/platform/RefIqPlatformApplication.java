package com.refiq.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;


@EnableRetry
@SpringBootApplication
public class RefIqPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(RefIqPlatformApplication.class, args);
  }





}
