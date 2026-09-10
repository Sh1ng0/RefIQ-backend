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
 * Defines a base skeleton for Webhook tests protected by an API Key.
 * <p>
 * Disables standard Spring JWT security and exclusively loads the
 * MinIO token validation filter.
 * </p>
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

  protected static final String WEBHOOK_TOKEN_HEADER = "X-RefIQ-Webhook-Token";
  protected static final String VALID_API_KEY = "test-webhook-key";
}