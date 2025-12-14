package com.refiq.platform.ingestion.internal.adapter.s3;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configuración del cliente de Amazon S3 (AWS SDK v2).
 * <p>
 * Esta clase se encarga de instanciar el bean {@link S3Client}, permitiendo flexibilidad
 * entre entornos de ejecución. Soporta tanto la conexión a la nube real de AWS como a
 * emuladores locales (como LocalStack o MinIO) mediante la sobreescritura del endpoint.
 * </p>
 */
@Configuration
class S3Config {

  @Value("${refiq.storage.s3.region}")
  private String region;

  /**
   * Endpoint opcional para sobrescribir la URL de servicio.
   * Útil para desarrollo local (LocalStack) o testing. Si está vacío, usa el default de AWS.
   */
  @Value("${refiq.storage.s3.endpoint:}")
  private String endpoint;

  // En un entorno productivo real, idealmente usaríamos DefaultCredentialsProvider
  // para asumir roles de IAM (Instance Profile / Pod Identity), pero para este MVP
  // o desarrollo local, las credenciales estáticas son suficientes.
  @Value("${aws.accessKeyId:test}")
  private String accessKey;

  @Value("${aws.secretAccessKey:test}")
  private String secretKey;

  /**
   * Crea y configura el cliente de S3.
   * <p>
   * La configuración detecta si existe un {@code endpoint} personalizado definido en las propiedades.
   * Si existe, configura el cliente para usar ese endpoint y fuerza el estilo de acceso por ruta
   * (path-style access), lo cual es mandatorio para emuladores como LocalStack.
   * </p>
   *
   * @return Un cliente {@link S3Client} listo para inyectar y usar.
   */
  @Bean
  public S3Client s3Client() {
    var builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        ));

    // Lógica para soportar entornos locales (LocalStack/MinIO)
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
          .forcePathStyle(true); // Crucial: http://localhost:4566/bucket en lugar de http://bucket.localhost:4566
    }

    return builder.build();
  }
}