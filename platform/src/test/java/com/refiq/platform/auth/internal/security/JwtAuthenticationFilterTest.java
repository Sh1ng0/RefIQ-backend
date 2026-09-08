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
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Filter: Ignores the request if there is no Authorization header")
  void shouldIgnoreRequest_WhenNoAuthHeader() throws Exception {
    // GIVEN

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Filter: Ignores the request if the header does not start with 'Bearer '")
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
  @DisplayName("Filter: Does not authenticate if the token is invalid (Provider returns empty)")
  void shouldNotAuthenticate_WhenTokenIsInvalid() throws Exception {
    // GIVEN
    request.addHeader("Authorization", "Bearer fake-or-expired-token");
    when(jwtProvider.validateAndExtractUserId("fake-or-expired-token")).thenReturn(Optional.empty());

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Filter: Authenticates successfully by injecting the UUID into the context")
  void shouldAuthenticate_WhenTokenIsValid() throws Exception {
    // GIVEN
    UUID userId = UUID.randomUUID();
    request.addHeader("Authorization", "Bearer super.secret.token");
    when(jwtProvider.validateAndExtractUserId("super.secret.token")).thenReturn(Optional.of(userId));

    // WHEN
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // THEN
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo(userId);
    assertThat(auth.getCredentials()).isNull();

    verify(filterChain).doFilter(request, response);
  }
}