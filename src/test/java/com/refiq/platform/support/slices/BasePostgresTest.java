package com.refiq.platform.support.slices;

import com.refiq.platform.support.containers.GlobalPostgresContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base slice configuration for integration tests requiring a PostgreSQL database.
 * <p>
 * Inheriting from this class automatically wires the application properties to the
 * shared Singleton Testcontainer, ensuring Flyway migrations run and the data source
 * is correctly configured before the Spring context starts.
 * </p>
 */
@ActiveProfiles("test")
@TestPropertySource(properties = {
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