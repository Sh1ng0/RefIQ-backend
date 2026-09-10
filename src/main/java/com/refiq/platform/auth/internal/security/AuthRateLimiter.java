package com.refiq.platform.auth.internal.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



// TODO: Investigate delegating IP rate limiting to NGINX.
// Currently prepared for proxy environments (X-Forwarded-For), pending NGINX configuration.
// TECHNICAL DEBT: Implement strict rate limiting by email to prevent dictionary attacks.
/**
 * Unified in-memory rate limiter to mitigate brute-force and spam attacks.
 * Protects the login flow (by email) and the registration flow (by IP).
 */
/**
 * Unified in-memory rate limiter to mitigate brute-force and spam attacks.
 * Protects the login flow (by email) and the registration flow (by IP).
 */
@Component

public class AuthRateLimiter {

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Limits login attempts per email address.
   * Allows 5 attempts every 15 minutes.
   *
   * @param email The target email address.
   * @return true if the request is allowed, false if the limit has been reached.
   */
  public boolean tryConsumeLogin(String email) {
    Bucket bucket = buckets.computeIfAbsent("login:" + email, key -> createLoginBucket());
    return bucket.tryConsume(1);
  }

  /**
   * Limits registration attempts per IP address.
   * Allows 3 registrations every 1 hour (Adjustable based on needs).
   *
   * @param ip The client's IP address.
   * @return true if the request is allowed, false if the limit has been reached.
   */
  public boolean tryConsumeRegister(String ip) {
    Bucket bucket = buckets.computeIfAbsent("register:" + ip, key -> createRegisterBucket());
    return bucket.tryConsume(1);
  }

  private Bucket createLoginBucket() {
    Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
    return Bucket.builder().addLimit(limit).build();
  }

  private Bucket createRegisterBucket() {

    Bandwidth limit = Bandwidth.classic(3, Refill.greedy(3, Duration.ofHours(1)));
    return Bucket.builder().addLimit(limit).build();
  }
}