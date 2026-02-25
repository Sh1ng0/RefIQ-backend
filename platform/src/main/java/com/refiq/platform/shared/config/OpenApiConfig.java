package com.refiq.platform.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  /**
   * Global configuration for the API documentation.
   * Defines the API title, version, and security schemes (JWT/Basic) used across all modules.
   */
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("RefIQ Platform API")
            .version("1.0.0")
            .description("Backend platform for Clinical Reference Interval Quantification using RefineR.")
            .contact(new Contact()
                .name("RefIQ Team")
                .email("dev@refiq.platform")))
        // We define the Security Scheme here once.
        // Modules (like Auth) will reference this by name ("basicAuth" or "bearerAuth").
        .components(new Components()
            .addSecuritySchemes("basicAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")));
  }

  /**
   * Group for the Ingestion Module.
   * Scans only the 'ingestion' package to keep the Swagger UI clean.
   */
  @Bean
  public GroupedOpenApi ingestionApi() {
    return GroupedOpenApi.builder()
        .group("ingestion")
        .packagesToScan("com.refiq.platform.ingestion")
        .build();
  }

  /**
   * Group for the Calculation Module.
   */
  @Bean
  public GroupedOpenApi calculationApi() {
    return GroupedOpenApi.builder()
        .group("calculation")
        .packagesToScan("com.refiq.platform.calculation")
        .build();
  }

  /**
   * Group for the Auth Module (Future proofing).
   */
  @Bean
  public GroupedOpenApi authApi() {
    return GroupedOpenApi.builder()
        .group("auth")
        .packagesToScan("com.refiq.platform.auth")
        .build();
  }
}