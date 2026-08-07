package com.refiq.platform.support.slices;

import com.refiq.platform.support.containers.GlobalS3Container;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;


public abstract class BaseS3Test extends BasePostgresTest {

  protected static final LocalStackContainer s3Container = GlobalS3Container.INSTANCE.getContainer();

  @DynamicPropertySource
  static void s3Properties(DynamicPropertyRegistry registry) {

    String dynamicEndpoint = s3Container.getEndpointOverride(LocalStackContainer.Service.S3).toString();



    registry.add("refiq.storage.s3.endpoint", () -> dynamicEndpoint);
    registry.add("refiq.storage.s3.region", s3Container::getRegion);
    registry.add("aws.accessKeyId", s3Container::getAccessKey);
    registry.add("aws.secretAccessKey", s3Container::getSecretKey);

    registry.add("refiq.storage.s3.bucket-name", () -> GlobalS3Container.TEST_BUCKET);
    registry.add("refiq.datalake.api.url", () -> "http://localhost:8080/mock-datalake");
  }
}