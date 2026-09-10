package com.refiq.platform.support.slices;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Acts as a base skeleton for testing HTTP clients that interact with external APIs.
 * <p>
 * Spins up WireMock on an ephemeral port and mocks the AWS S3 layer
 * to avoid depending on real credentials during test execution.
 * </p>
 */
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
public abstract class WireMockPlumberTest {

  @Autowired
  protected ObjectMapper objectMapper;

  @MockitoBean
  protected S3Presigner s3Presigner;
}