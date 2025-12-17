package com.refiq.platform.modules.ingestion;



import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.io.IOException;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Base para tests de integración que requieran infraestructura AWS (S3).
 * Gestiona el ciclo de vida del contenedor LocalStack y la inyección de propiedades.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Container
  static LocalStackContainer localStack = new LocalStackContainer(
      DockerImageName.parse("localstack/localstack:3.0")
  ).withServices(S3);

  @DynamicPropertySource
  static void overrideConfiguration(DynamicPropertyRegistry registry) {

    registry.add("refiq.storage.s3.endpoint", () -> localStack.getEndpointOverride(S3).toString());
    registry.add("refiq.storage.s3.region", localStack::getRegion);
    registry.add("aws.accessKeyId", localStack::getAccessKey);
    registry.add("aws.secretAccessKey", localStack::getSecretKey);
    registry.add("refiq.storage.s3.bucket-name", () -> BUCKET_NAME);
  }

  /**
   * Inicialización de recursos dentro de LocalStack antes de que corran los tests.
   * Crea el bucket necesario para que la aplicación no falle al subir.
   */
  @BeforeAll
  static void beforeAll() throws IOException, InterruptedException {
    localStack.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET_NAME);
  }
}