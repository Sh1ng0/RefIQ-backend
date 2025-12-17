package com.refiq.platform.ingestion.internal.adapter.s3;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Configuración del cliente de Amazon S3 (AWS SDK v2).
 * <p>
 * Gestiona la conexión de forma híbrida:
 * <ul>
 * <li><strong>Perfil Dev/Local:</strong> Usa endpoint personalizado (LocalStack) y credenciales estáticas.</li>
 * <li><strong>Perfil Prod:</strong> Usa conexión estándar a AWS y resuelve credenciales vía IAM Role o Env Vars.</li>
 * </ul>
 */
@Configuration
class S3Config {

  @Value("${refiq.storage.s3.region}")
  private String region;

  @Value("${refiq.storage.s3.endpoint:}") // Por defecto vacío
  private String endpoint;

  @Value("${aws.accessKeyId:}") // Por defecto vacío para permitir IAM Roles en prod
  private String accessKey;

  @Value("${aws.secretAccessKey:}")
  private String secretKey;

  @Bean
  public S3Client s3Client() {
    var builder = S3Client.builder()
        .region(Region.of(region));

    // LÓGICA DE DETECCIÓN DE ENTORNO

    if (endpoint != null && !endpoint.isBlank()) {
      // CASO 1 LOCALSTACK
      // Si hay endpoint explícito, asumimos entorno local/emulado.
      builder.endpointOverride(URI.create(endpoint))
          .forcePathStyle(true); // Necesario para LocalStack


      String localKey = accessKey.isBlank() ? "test" : accessKey;
      String localSecret = secretKey.isBlank() ? "test" : secretKey;

      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localKey, localSecret)
      ));

    } else {
      // CASO 2: PRODUCCIÓN


      if (!accessKey.isBlank() && !secretKey.isBlank()) {

        builder.credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        ));
      } else {

        builder.credentialsProvider(DefaultCredentialsProvider.create());
      }
    }

    return builder.build();
  }
}