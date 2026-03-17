package com.refiq.platform.auth.internal.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa en memoria para mitigar ataques de fuerza bruta en el login.
 */
@Component
@Profile({"!test", "security"})
public class LoginRateLimiter {

  // Almacenamos un "cubo" por cada email. ConcurrentHashMap es thread-safe.
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Comprueba si el email aún tiene intentos disponibles.
   * Si los tiene, consume un intento y devuelve true. Si no, devuelve false.
   */
  public boolean tryConsume(String email) {
    Bucket bucket = buckets.computeIfAbsent(email, this::createNewBucket);
    return bucket.tryConsume(1);
  }

  private Bucket createNewBucket(String email) {
    // 5 intentos cada 15 minutos
    Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
    return Bucket.builder()
        .addLimit(limit)
        .build();
  }
}