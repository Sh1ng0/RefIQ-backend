package com.refiq.platform.support.slices;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.internal.config.SecurityConfig;
import com.refiq.platform.auth.internal.security.JwtAuthenticationFilter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.auth.internal.security.WebhookApiKeyFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Acts as a base skeleton for controller tests (WebMvcTest) that require the complete security context.
 * <p>
 * Configures MockMvc and sets up the real Spring Security filters while mocking the
 * cryptographic provider to avoid processing overhead during tests.
 * </p>
 */
@ActiveProfiles({"test", "security"})
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    WebhookApiKeyFilter.class
})
@TestPropertySource(properties = {
    "refiq.security.cors.allowed-origins=*",
    "refiq.webhooks.minio.api-key=test-webhook-key",
    "refiq.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
    "refiq.security.jwt.expiration-ms=900000"
})
public abstract class BaseWebWithAuthTest {

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  @MockitoBean
  protected JwtProvider jwtProvider;
}