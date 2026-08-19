package com.refiq.platform.support.slices;

import com.refiq.platform.support.containers.GlobalPostgresContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
    // Activamos Flyway para que inyecte V1__init_schema.sql en Testcontainers
    "spring.flyway.enabled=true"
})
public abstract class BasePostgresTest {

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    PostgreSQLContainer<?> postgres = GlobalPostgresContainer.INSTANCE.getContainer();

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }
}