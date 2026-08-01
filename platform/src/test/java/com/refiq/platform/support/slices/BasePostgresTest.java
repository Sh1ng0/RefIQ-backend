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
    "spring.jpa.hibernate.ddl-auto=create-drop", // Igual que tenías en AuthBaseIntegrationTest
    "spring.flyway.enabled=false"                // Apagamos Flyway para evitar conflictos entre módulos aislados
})
public abstract class BasePostgresTest {

  /**
   * Inyección explícita e indestructible del Singleton.
   * Spring inyectará estas propiedades en su contexto antes de intentar levantar JPA.
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    PostgreSQLContainer<?> postgres = GlobalPostgresContainer.INSTANCE.getContainer();

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }
}