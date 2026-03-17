package com.refiq.platform.auth.api;


import com.refiq.platform.RefIqPlatformApplication;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Clase base para los tests de integración del módulo de Auth. Levanta un PostgreSQL efímero y
 * ultra-rápido solo para estos tests.
 */
@Testcontainers
@SpringBootTest(
    classes = RefIqPlatformApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",

        "refiq.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "refiq.security.jwt.expiration-ms=900000",
        "spring.autoconfigure.exclude=",
        "refiq.security.cors.allowed-origins=*"
    }
)
@ActiveProfiles({"test", "security"})
public abstract class AuthBaseIntegrationTest {


  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

}