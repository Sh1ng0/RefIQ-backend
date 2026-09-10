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

  @Test
  @DisplayName("Login: Allows exactly 5 attempts before blocking")
  void shouldAllowFiveLoginAttemptsAndBlockTheSixth() {
    String email = "hacker@refiq.com";

    for (int i = 0; i < 5; i++) {
      boolean result = rateLimiter.tryConsumeLogin(email);
      assertThat(result)
          .withFailMessage("Login attempt " + (i + 1) + " should have been allowed")
          .isTrue();
    }

    boolean blockedResult = rateLimiter.tryConsumeLogin(email);
    assertThat(blockedResult).isFalse();
  }

  @Test
  @DisplayName("Login: Isolation - Blocking one email does not affect another")
  void shouldIsolateLoginRateLimitsByEmail() {
    String attackerEmail = "attacker@refiq.com";
    String innocentEmail = "innocent@refiq.com";

    for (int i = 0; i < 5; i++) {
      rateLimiter.tryConsumeLogin(attackerEmail);
    }

    assertThat(rateLimiter.tryConsumeLogin(attackerEmail)).isFalse();
    assertThat(rateLimiter.tryConsumeLogin(innocentEmail)).isTrue();
  }

  @Test
  @DisplayName("Register: Allows exactly 3 attempts before blocking")
  void shouldAllowThreeRegisterAttemptsAndBlockTheFourth() {
    String ip = "192.168.1.100";

    for (int i = 0; i < 3; i++) {
      boolean result = rateLimiter.tryConsumeRegister(ip);
      assertThat(result)
          .withFailMessage("Register attempt " + (i + 1) + " should have been allowed")
          .isTrue();
    }

    boolean blockedResult = rateLimiter.tryConsumeRegister(ip);
    assertThat(blockedResult).isFalse();
  }

  @Test
  @DisplayName("Register: Isolation - Blocking one IP does not affect another")
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