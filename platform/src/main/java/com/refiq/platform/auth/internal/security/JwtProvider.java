package com.refiq.platform.auth.internal.security;




import com.refiq.platform.auth.internal.service.AuthLogEvent;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Internal cryptographic component responsible for the issuance and validation of JSON Web Tokens (JWT).
 * <p>
 * This class acts as a secure facade over the underlying JJWT library, isolating the rest
 * of the authentication module from the complexities of cryptographic signing, parsing, and
 * exception handling.
 * </p>
 * <p>
 * Key behaviors include:
 * <ul>
 * <li><b>Token Generation:</b> Cryptographically signs the user's UUID into the {@code sub} (Subject)
 * claim using the HMAC-SHA algorithm, applying the configured time-to-live (TTL).</li>
 * <li><b>Stateless Validation:</b> Parses and verifies the token's digital signature and expiration
 * date in memory, without requiring a database lookup.</li>
 * <li><b>Exception Shielding:</b> Catches all library-specific cryptographic or parsing exceptions
 * (e.g., {@code ExpiredJwtException}, {@code SignatureException}) and safely returns an {@link Optional#empty()},
 * adhering to the project's Data-Oriented Programming (DOP) approach by avoiding control flow via exceptions.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Profile Configuration:</b> Active by default ({@code !test}). Excluded in standard
 * testing environments to allow seamless integration testing of other modules without
 * requiring actual cryptographic operations, unless the {@code security} profile is explicitly activated.
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

  // Aquí engordamos el Token
  public String generateToken(UUID userId ) { // String email

    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userId.toString())
        //.claim("email", email)
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
      AuthLogEvent.JWT_VALIDATION_FAILED.log(log, e.getMessage());
      return Optional.empty();
    }
  }


}