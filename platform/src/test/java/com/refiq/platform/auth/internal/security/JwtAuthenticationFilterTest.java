package com.refiq.platform.auth.internal.security;



import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.refiq.platform.auth.internal.security.JwtAuthenticationFilter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter - Unit Tests")
class JwtAuthenticationFilterTest {

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private FilterChain filterChain;

  @InjectMocks
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    // Spring nos da estas clases para simular peticiones HTTP sin levantar Tomcat
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void tearDown() {
    // ¡VITAL! El SecurityContextHolder guarda los datos en el hilo actual (ThreadLocal).
    // Hay que limpiarlo después de cada test para que no contamine al siguiente.
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Filtro: Ignora la petición si no hay cabecera Authorization")
  void shouldIgnoreRequest_WhenNoAuthHeader() throws Exception {
    // GIVEN

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Filtro: Ignora la petición si la cabecera no empieza por 'Bearer '")
  void shouldIgnoreRequest_WhenAuthHeaderIsMalformed() throws Exception {
    // GIVEN
    request.addHeader("Authorization", "Basic user:password");

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Filtro: No autentica si el token es inválido (Provider devuelve empty)")
  void shouldNotAuthenticate_WhenTokenIsInvalid() throws Exception {
    // GIVEN
    request.addHeader("Authorization", "Bearer token-falso-o-caducado");
    when(jwtProvider.validateAndExtractUserId("token-falso-o-caducado")).thenReturn(Optional.empty());

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Filtro: Autentica correctamente inyectando el UUID en el contexto")
  void shouldAuthenticate_WhenTokenIsValid() throws Exception {
    // GIVEN
    UUID userId = UUID.randomUUID();
    request.addHeader("Authorization", "Bearer token.super.secreto");
    when(jwtProvider.validateAndExtractUserId("token.super.secreto")).thenReturn(Optional.of(userId));

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo(userId); // El principal DEBE ser tu UUID
    assertThat(auth.getCredentials()).isNull();

    verify(filterChain).doFilter(request, response);
  }
}