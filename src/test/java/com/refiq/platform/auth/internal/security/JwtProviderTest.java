package com.refiq.platform.auth.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.refiq.platform.auth.internal.security.JwtProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProvider - Unit Tests")
class JwtProviderTest {

  private static final String SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
  private static final long EXPIRATION_MS = 900000;

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(SECRET, EXPIRATION_MS);
  }

  @Test
  @DisplayName("Happy Path: Generates a token and extracts the UUID correctly")
  void shouldGenerateAndValidateTokenSuccessfully() {
    // GIVEN
    UUID userId = UUID.randomUUID();

    // WHEN
    String token = jwtProvider.generateToken(userId);
    Optional<UUID> extractedId = jwtProvider.validateAndExtractUserId(token);

    // THEN
    assertThat(token).isNotBlank();
    assertThat(extractedId).isPresent();
    assertThat(extractedId.get()).isEqualTo(userId);
  }

  @Test
  @DisplayName("Security: Fails silently (Optional.empty) with a malformed or garbage token")
  void shouldReturnEmptyWhenTokenIsMalformed() {
    // GIVEN
    String garbageToken = "this.is.not.a.jwt";

    // WHEN
    Optional<UUID> extractedId = jwtProvider.validateAndExtractUserId(garbageToken);

    // THEN
    assertThat(extractedId).isEmpty();
  }

  @Test
  @DisplayName("Security: Fails silently if the token has expired")
  void shouldReturnEmptyWhenTokenIsExpired() {
    // GIVEN
    JwtProvider expiredProvider = new JwtProvider(SECRET, -1000);
    UUID userId = UUID.randomUUID();
    String expiredToken = expiredProvider.generateToken(userId);

    // WHEN
    Optional<UUID> extractedId = expiredProvider.validateAndExtractUserId(expiredToken);

    // THEN
    assertThat(extractedId).isEmpty();
  }

  @Test
  @DisplayName("Security: Fails silently if the token was signed with a different key")
  void shouldReturnEmptyWhenSignatureIsInvalid() {
    // GIVEN
    UUID userId = UUID.randomUUID();
    String validToken = jwtProvider.generateToken(userId);

    String hackerSecret = "OTg3NjU0MzIxMDk4NzY1NDMyMTA5ODc2NTQzMjEwOTg=";
    JwtProvider hackedProvider = new JwtProvider(hackerSecret, EXPIRATION_MS);

    // WHEN
    Optional<UUID> extractedId = hackedProvider.validateAndExtractUserId(validToken);

    // THEN
    assertThat(extractedId).isEmpty();
  }
}