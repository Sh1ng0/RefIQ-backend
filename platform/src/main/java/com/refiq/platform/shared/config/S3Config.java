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

/**
 * Configures the shared Amazon S3 infrastructure.
 * <p>
 * Establishes the beans required to interact with object storage. It is designed to work
 * transparently across different environments:
 * <ul>
 * <li><strong>Production (AWS):</strong> Uses standard region and IAM/Key credentials.</li>
 * <li><strong>Development (LocalStack/MinIO):</strong> Supports endpoint overriding, path-style access,
 * and specific Docker network routing for presigned URLs.</li>
 * </ul>
 * </p>
 */
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

  /**
   * Special endpoint for presigned URLs.
   * Required when the address used by the backend to reach S3 (e.g., internal Docker network)
   * differs from the address used by external clients (e.g., localhost or public URL).
   */
  @Value("${refiq.storage.s3.presigned-endpoint:}")
  private String presignedEndpoint;

  /**
   * Configures and provides the synchronous {@link S3Client}.
   * <p>
   * <strong>Implementation Note:</strong> Forces {@code pathStyleAccessEnabled(true)} to ensure
   * compatibility with LocalStack and MinIO, which often reject the default DNS-style bucket addressing.
   * </p>
   *
   * @return The configured S3 client ready for I/O operations.
   */
  @Bean
  public S3Client s3Client() {
    var builder = S3Client.builder()
        .region(Region.of(region))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(true) // Crucial for LocalStack/MinIO compatibility
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

  /**
   * Configures and provides the {@link S3Presigner} for generating temporary access URLs.
   * <p>
   * This bean is critical for the Calculation module, allowing the R engine to download files
   * securely without needing permanent AWS credentials.
   * </p>
   * <p>
   * It prioritizes {@code presigned-endpoint} over the standard {@code endpoint} to solve
   * Docker networking scenarios where the container URL differs from the host URL.
   * </p>
   *
   * @return The configured S3 Presigner.
   */
  @Bean
  public S3Presigner s3Presigner() {
    var builder = S3Presigner.builder()
        .region(Region.of(region));

    // S3 configuration to force Path Style.
    // This is vital for LocalStack/MinIO to function properly within Docker networks.
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