package com.refiq.platform.modules.ingestion;



import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.io.IOException;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Base class for integration tests that require AWS infrastructure (S3).
 * Manages the lifecycle of the LocalStack container and property injection.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {


  private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  static org.testcontainers.containers.Network network = org.testcontainers.containers.Network.newNetwork();

  @Container
  static LocalStackContainer localStack = new LocalStackContainer(
      DockerImageName.parse("localstack/localstack:3.0")
  )
      .withServices(S3)
      .withNetwork(network)
      .withNetworkAliases("s3.localstack")
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("LOCALSTACK")));

  @Container
  static GenericContainer<?> rEngine = new GenericContainer<>(
      new ImageFromDockerfile("refiq-engine", false)

          .withFileFromPath(".", Path.of("src/main/java/com/refiq/engine")))
      .withExposedPorts(8000)
      .withNetwork(network)
      .withNetworkAliases("r-engine")
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("R-ENGINE")));

  @DynamicPropertySource
  static void overrideConfiguration(DynamicPropertyRegistry registry) {
    // Para Java, LocalStack sigue estando en localhost (vía puerto mapeado aleatorio)
    registry.add("refiq.storage.s3.endpoint", () -> localStack.getEndpointOverride(S3).toString());
    registry.add("refiq.storage.s3.region", localStack::getRegion);
    registry.add("aws.accessKeyId", localStack::getAccessKey);
    registry.add("aws.secretAccessKey", localStack::getSecretKey);
    registry.add("refiq.storage.s3.bucket-name", () -> BUCKET_NAME);

    registry.add("refiq.storage.s3.presigned-endpoint", () -> "http://s3.localstack:4566");

    // Inyectamos la URL del contenedor R para el PlumberAdapter
    registry.add("plumber.api.url", () ->
        "http://" + rEngine.getHost() + ":" + rEngine.getMappedPort(8000));
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