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
 * Esqueleto base para tests de controladores (WebMvcTest) que requieren el contexto de seguridad
 * completo. Carga MockMvc, levanta los filtros reales de Spring Security pero mockea el proveedor
 * criptográfico para evitar sobrecarga.
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