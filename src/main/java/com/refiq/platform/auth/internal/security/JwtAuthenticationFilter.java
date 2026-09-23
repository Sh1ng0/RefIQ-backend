package com.refiq.platform.auth.internal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Core security filter that intercepts incoming HTTP requests to validate JSON Web Tokens (JWT).
 * <p>
 * This filter extracts the token from the {@code Authorization} header using the Bearer schema and
 * delegates its validation to the {@link JwtProvider}. If the token is valid, it extracts the
 * user's UUID and populates the
 * {@link org.springframework.security.core.context.SecurityContextHolder}, authorizing the request
 * to proceed. If the token is missing or invalid, the security context remains empty, delegating
 * the rejection to the Spring Security chain.
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;

  public JwtAuthenticationFilter(JwtProvider jwtProvider) {
    this.jwtProvider = jwtProvider;
  }

  @Override

  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {

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