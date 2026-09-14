package com.refiq.platform.auth.internal.security;

import com.refiq.platform.auth.internal.logging.AuthLogEvent; // <-- Actualizado al nuevo paquete transversal
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Cryptographic component responsible for the issuance and validation of JSON Web Tokens (JWT).
 * <p>
 * Key behaviors include:
 * <ul>
 * <li><b>Token Generation:</b> Signs the user's UUID into the {@code sub} (Subject) claim using
 * the HMAC-SHA algorithm with the configured time-to-live.</li>
 * <li><b>Validation:</b> Parses and verifies the token's digital signature and expiration date.</li>
 * <li><b>Exception Shielding:</b> Catches cryptographic and parsing exceptions from the underlying
 * JJWT library, returning an {@link Optional#empty()} and logging the failure.</li>
 * </ul>
 * </p>
 */
@Component
public class JwtProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

  private final SecretKey key;
  private final long expirationMs;

  public JwtProvider(
      @Value("${refiq.security.jwt.secret}") String secretBase64,
      @Value("${refiq.security.jwt.expiration-ms}") long expirationMs) {

    byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirationMs = expirationMs;
  }

  public String generateToken(UUID userId) {

    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
  }

  public Optional<UUID> validateAndExtractUserId(String token) {
    try {
      String subject = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();

      return Optional.of(UUID.fromString(subject));
    } catch (Exception e) {

      new AuthLogEvent.JwtValidationFailed(e.getMessage()).log(log);
      return Optional.empty();
    }
  }
}