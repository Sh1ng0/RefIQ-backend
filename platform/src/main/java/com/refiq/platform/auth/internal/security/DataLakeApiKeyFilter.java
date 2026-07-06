package com.refiq.platform.auth.internal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Deprecated
@Component
@RequiredArgsConstructor
public class DataLakeApiKeyFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-RefIQ-Data-Token";


  @Value("${refiq.datalake.api-key}")
  private String expectedApiKey;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {


    String requestApiKey = request.getHeader(API_KEY_HEADER);


    if (requestApiKey == null || !requestApiKey.equals(expectedApiKey)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.getWriter().write("Forbidden: Invalid or missing Data Lake API Key");
      return;
    }


    filterChain.doFilter(request, response);
  }

  // IMPORTANTE: Este filtro SOLO debe ejecutarse para las rutas de calculation
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/v1/calculations");
  }
}