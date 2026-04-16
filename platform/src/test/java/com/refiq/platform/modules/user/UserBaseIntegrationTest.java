package com.refiq.platform.modules.user;

import com.refiq.platform.auth.api.AuthBaseIntegrationTest;
import com.refiq.platform.user.internal.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Clase base para los tests del módulo User.
 * Hereda la infraestructura de PostgreSQL y Seguridad de AuthBaseIntegrationTest.
 */
public abstract class UserBaseIntegrationTest extends AuthBaseIntegrationTest {

  @Autowired
  protected UserProfileRepository userProfileRepository;

  @AfterEach
  void cleanUpUserProfiles() {
    userProfileRepository.deleteAll();
  }
}