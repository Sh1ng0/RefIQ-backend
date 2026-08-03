package com.refiq.platform.support.containers;


import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Patrón Singleton (Bloch Enum) para garantizar que LocalStack se levanta UNA SOLA VEZ por cada
 * ejecución de la suite completa de tests de Ingestión.
 */
public enum GlobalS3Container {

  INSTANCE;

  private final LocalStackContainer container;

  public static final String TEST_BUCKET = "refiq-test-bucket";

  GlobalS3Container() {

    container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.2"))
        .withServices(LocalStackContainer.Service.S3);

    container.start();

    try {
      container.execInContainer("awslocal", "s3", "mb", "s3://" + TEST_BUCKET);
    } catch (Exception e) {
      throw new RuntimeException("Fallo al inicializar el bucket de S3 en LocalStack", e);
    }
  }

  public LocalStackContainer getContainer() {
    return container;
  }
}