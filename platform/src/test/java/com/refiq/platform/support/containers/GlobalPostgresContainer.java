package com.refiq.platform.support.containers;



import org.testcontainers.containers.PostgreSQLContainer;


public enum GlobalPostgresContainer {

  INSTANCE;

  private final PostgreSQLContainer<?> container;

  @SuppressWarnings("resource") // Ryuk will close the resoruces when the JVM stops it's work
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