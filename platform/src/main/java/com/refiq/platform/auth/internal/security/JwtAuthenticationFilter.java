package com.refiq.platform.auth.internal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Core security filter that intercepts incoming HTTP requests to validate JSON Web Tokens (JWT).
 * <p>
 * Adhering strictly to the stateless architecture of the application, this filter extracts the
 * token from the {@code Authorization} header (using the Bearer schema) and cryptographically
 * verifies it via the {@link JwtProvider}. It deliberately avoids database queries during the
 * request lifecycle to ensure maximum performance and maintain true statelessness.
 * </p>
 * <p>
 * If the token is valid, the filter extracts the user's UUID and populates the Spring
 * {@link org.springframework.security.core.context.SecurityContextHolder}, authorizing the request
 * to proceed. If the token is missing, expired, or malformed, the security context remains empty,
 * and the request is subsequently rejected by the Spring Security chain.
 * </p>
 * <p>
 * <b>Profile Configuration:</b> Active by default ({@code !test}). Excluded in standard
 * testing environments to allow seamless integration testing of other modules without requiring
 * valid JWTs, unless the {@code security} profile is explicitly activated.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Profile({"!test", "security"})
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(7);

    jwtProvider.validateAndExtractUserId(token).ifPresent(userId -> {

      var authentication = new UsernamePasswordAuthenticationToken(
          userId,
          null,
          Collections.emptyList()
      );

      SecurityContextHolder.getContext().setAuthentication(authentication);
    });

    filterChain.doFilter(request, response);
  }
}