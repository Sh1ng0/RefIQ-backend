package com.refiq.platform.support.containers;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Implements the Singleton pattern (Bloch Enum) to ensure LocalStack is started ONLY ONCE
 * per execution of the entire test suite.
 * <p>
 * This significantly reduces test execution time by sharing the same S3 container context
 * across multiple integration tests, simulating the Data Lake environment.
 * </p>
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
      throw new RuntimeException("Failed to initialize the S3 bucket in LocalStack", e);
    }
  }

  public LocalStackContainer getContainer() {
    return container;
  }
}