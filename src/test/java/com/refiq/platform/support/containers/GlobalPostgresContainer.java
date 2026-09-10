package com.refiq.platform.support.containers;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Implements the Singleton pattern (Bloch Enum) to ensure PostgreSQL is started ONLY ONCE
 * per execution of the entire test suite.
 * <p>
 * Utilizes Testcontainers' reuse feature to drastically reduce context load times
 * across different slice tests while maintaining database isolation.
 * </p>
 */
public enum GlobalPostgresContainer {

  INSTANCE;

  private final PostgreSQLContainer<?> container;

  @SuppressWarnings("resource") // Ryuk will close the resources when the JVM stops its work
  GlobalPostgresContainer() {
    container = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("refiq_test_db")
        .withUsername("test_user")
        .withPassword("test_pass")
        .withReuse(true);

    container.start();
  }

  public PostgreSQLContainer<?> getContainer() {
    return container;
  }
}