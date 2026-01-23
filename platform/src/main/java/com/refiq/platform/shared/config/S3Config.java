package com.refiq.platform.shared.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

  @Value("${refiq.storage.s3.region}")
  private String region;

  @Value("${refiq.storage.s3.endpoint:}")
  private String endpoint;

  @Value("${aws.accessKeyId:}")
  private String accessKey;

  @Value("${aws.secretAccessKey:}")
  private String secretKey;

  @Value("${refiq.storage.s3.presigned-endpoint:}")
  private String presignedEndpoint;

  @Bean
  public S3Client s3Client() {
    var builder = S3Client.builder()
        .region(Region.of(region))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(true) // Crucial para LocalStack
            .build());

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));

      String localKey = accessKey.isBlank() ? "test" : accessKey;
      String localSecret = secretKey.isBlank() ? "test" : secretKey;

      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localKey, localSecret)
      ));
    } else {
      if (!accessKey.isBlank() && !secretKey.isBlank()) {
        builder.credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        ));
      }
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    var builder = S3Presigner.builder()
        .region(Region.of(region));

    // La configuración de S3 para forzar el Path Style
    // Esto es vital para que LocalStack funcione en redes de Docker
    builder.serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build());

    String effectiveEndpoint = (!presignedEndpoint.isBlank()) ? presignedEndpoint : endpoint;

    if (effectiveEndpoint != null && !effectiveEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(effectiveEndpoint));

      String localKey = accessKey.isBlank() ? "test" : accessKey;
      String localSecret = secretKey.isBlank() ? "test" : secretKey;

      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localKey, localSecret)
      ));
    } else {
      if (!accessKey.isBlank() && !secretKey.isBlank()) {
        builder.credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        ));
      }
    }
    return builder.build();
  }


}