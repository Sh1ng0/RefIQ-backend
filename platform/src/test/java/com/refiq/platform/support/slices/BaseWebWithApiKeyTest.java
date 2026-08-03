package com.refiq.platform.support.slices;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.auth.internal.security.WebhookApiKeyFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Esqueleto base para tests de Webhooks protegidos por API Key.
 * Apaga la seguridad JWT estándar de Spring y carga únicamente el filtro
 * de validación de tokens de MinIO.
 */
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(WebhookApiKeyFilter.class)
@TestPropertySource(properties = {

    "refiq.webhooks.minio.api-key=test-webhook-key"
})
public abstract class BaseWebWithApiKeyTest {

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  @MockitoBean
  protected JwtProvider jwtProvider;

  // Constantes para que los tests hijos las usen fácilmente
  protected static final String WEBHOOK_TOKEN_HEADER = "X-RefIQ-Webhook-Token";
  protected static final String VALID_API_KEY = "test-webhook-key";
}