package com.refiq.platform.support.slices;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Esqueleto base para testear clientes HTTP que llaman a APIs externas.
 * Levanta WireMock en un puerto efímero y mockea la capa de AWS S3
 * para evitar depender de credenciales reales.
 */
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
public abstract class WireMockPlumberTest {

  @Autowired
  protected ObjectMapper objectMapper;


  @MockitoBean
  protected S3Presigner s3Presigner;
}