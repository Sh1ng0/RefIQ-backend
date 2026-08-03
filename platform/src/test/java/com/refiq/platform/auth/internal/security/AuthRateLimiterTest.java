package com.refiq.platform.auth.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;



@DisplayName("AuthRateLimiter - Unit Tests")
class AuthRateLimiterTest {

  private AuthRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter = new AuthRateLimiter();
  }

  // --- TESTS DE LOGIN (Límite: 5) ---

  @Test
  @DisplayName("Login: Permite exactamente 5 intentos antes de bloquear")
  void shouldAllowFiveLoginAttemptsAndBlockTheSixth() {
    String email = "hacker@refiq.com";

    for (int i = 0; i < 5; i++) {
      boolean result = rateLimiter.tryConsumeLogin(email);
      assertThat(result)
          .withFailMessage("El intento de login " + (i + 1) + " debería haber sido permitido")
          .isTrue();
    }

    boolean blockedResult = rateLimiter.tryConsumeLogin(email);
    assertThat(blockedResult).isFalse();
  }

  @Test
  @DisplayName("Login: Aislamiento - El bloqueo de un email no afecta a otro")
  void shouldIsolateLoginRateLimitsByEmail() {
    String attackerEmail = "attacker@refiq.com";
    String innocentEmail = "innocent@refiq.com";

    for (int i = 0; i < 5; i++) {
      rateLimiter.tryConsumeLogin(attackerEmail);
    }

    assertThat(rateLimiter.tryConsumeLogin(attackerEmail)).isFalse();
    assertThat(rateLimiter.tryConsumeLogin(innocentEmail)).isTrue();
  }

  // --- TESTS DE REGISTRO (Límite: 3) ---

  @Test
  @DisplayName("Registro: Permite exactamente 3 intentos antes de bloquear")
  void shouldAllowThreeRegisterAttemptsAndBlockTheFourth() {
    String ip = "192.168.1.100";

    for (int i = 0; i < 3; i++) {
      boolean result = rateLimiter.tryConsumeRegister(ip);
      assertThat(result)
          .withFailMessage("El intento de registro " + (i + 1) + " debería haber sido permitido")
          .isTrue();
    }

    boolean blockedResult = rateLimiter.tryConsumeRegister(ip);
    assertThat(blockedResult).isFalse();
  }

  @Test
  @DisplayName("Registro: Aislamiento - El bloqueo de una IP no afecta a otra")
  void shouldIsolateRegisterRateLimitsByIp() {
    String attackerIp = "10.0.0.5";
    String innocentIp = "10.0.0.8";

    for (int i = 0; i < 3; i++) {
      rateLimiter.tryConsumeRegister(attackerIp);
    }

    assertThat(rateLimiter.tryConsumeRegister(attackerIp)).isFalse();
    assertThat(rateLimiter.tryConsumeRegister(innocentIp)).isTrue();
  }
}