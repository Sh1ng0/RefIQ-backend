package com.refiq.platform.auth.internal.security;


import static org.assertj.core.api.Assertions.assertThat;

import com.refiq.platform.auth.internal.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginRateLimiter - Unit Tests")
class LoginRateLimiterTest {

  private LoginRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {

    rateLimiter = new LoginRateLimiter();
  }

  @Test
  @DisplayName("Permite exactamente 5 intentos antes de bloquear")
  void shouldAllowFiveAttemptsAndBlockTheSixth() {
    String email = "hacker@refiq.com";

    for (int i = 0; i < 5; i++) {
      boolean result = rateLimiter.tryConsume(email);
      assertThat(result)
          .withFailMessage("El intento " + (i + 1) + " debería haber sido permitido")
          .isTrue();
    }

    boolean blockedResult = rateLimiter.tryConsume(email);
    assertThat(blockedResult).isFalse();
  }

  @Test
  @DisplayName("Aislamiento: El bloqueo de un email no afecta a otro")
  void shouldIsolateRateLimitsByEmail() {
    String attackerEmail = "attacker@refiq.com";
    String innocentEmail = "innocent@refiq.com";

    for (int i = 0; i < 5; i++) {
      rateLimiter.tryConsume(attackerEmail);
    }

    assertThat(rateLimiter.tryConsume(attackerEmail)).isFalse();

    assertThat(rateLimiter.tryConsume(innocentEmail)).isTrue();
  }
}