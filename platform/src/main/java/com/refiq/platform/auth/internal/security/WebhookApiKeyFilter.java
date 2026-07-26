package com.refiq.platform.auth.internal.security;



import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class WebhookApiKeyFilter extends OncePerRequestFilter {

  private static final String WEBHOOK_TOKEN_HEADER = "X-RefIQ-Webhook-Token";
  private static final String WEBHOOK_PATH_PREFIX = "/api/v1/webhooks/";

  private final String configuredApiKey;

  public WebhookApiKeyFilter(@Value("${refiq.webhooks.minio.api-key:change-me-in-prod}") String configuredApiKey) {
    this.configuredApiKey = configuredApiKey;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {


    if (!request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }


    String providedKey = request.getHeader(WEBHOOK_TOKEN_HEADER);

    if (providedKey == null || !providedKey.equals(configuredApiKey)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing Webhook Token");
      return;
    }

    // Todo correcto, MinIO es quien dice ser
    filterChain.doFilter(request, response);
  }
}