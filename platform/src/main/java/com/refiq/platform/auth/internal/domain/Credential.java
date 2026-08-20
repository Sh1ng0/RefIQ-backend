package com.refiq.platform.auth.internal.domain;






import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representación central e inmutable de las credenciales de acceso de un usuario en RefIQ.
 * <p>
 * Diseñado bajo principios de Data-Oriented Programming: sin estado mutable,
 * sin dependencias de frameworks de persistencia y con transiciones de estado semánticas explícitas.
 * </p>
 */
public record Credential(
    UUID id,
    String email,
    String passwordHash,
    Instant createdAt
) {

  /**
   * Constructor canónico compacto.
   * Garantiza la integridad absoluta del dato en el momento de la instanciación,
   * ya venga de la base de datos o de un nuevo registro.
   */
  public Credential {
    Objects.requireNonNull(id, "El ID de la credencial no puede ser nulo");
    Objects.requireNonNull(email, "El email no puede ser nulo");
    Objects.requireNonNull(passwordHash, "El hash de la contraseña no puede ser nulo");
    Objects.requireNonNull(createdAt, "El timestamp de creación no puede ser nulo");
  }

  /**
   * Método de factoría estático para la creación de nuevas credenciales.
   * <p>
   * Este método absorbe la responsabilidad que antes delegábamos ciegamente en la
   * base de datos (@GeneratedValue) y en Hibernate (@PrePersist). Ahora el dominio
   * es dueño de su propia identidad y tiempo.
   * </p>
   */
  public static Credential createNew(String email, String encodedPassword) {
    return new Credential(
        UUID.randomUUID(),
        email.toLowerCase().trim(), // Normalización en la frontera del dominio
        encodedPassword,
        Instant.now()
    );
  }

  /**
   * Transición semántica de estado.
   * Expresa una intención clara de negocio en lugar de un simple "wither" mecánico.
   * Devuelve una nueva fotografía inmutable del estado.
   */
  public Credential updatePassword(String newEncodedPassword) {
    if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
      throw new IllegalArgumentException("El nuevo hash de contraseña es inválido");
    }

    return new Credential(
        this.id,
        this.email,
        newEncodedPassword,
        this.createdAt
    );
  }

  /**
   * Sobrescribimos el toString estándar de los records.
   * Mantenemos la restricción de seguridad de tu diseño original para garantizar
   * que el hash jamás se imprima accidentalmente en los logs de auditoría.
   */
  @Override
  public String toString() {
    return "Credential[" +
        "id=" + id + ", " +
        "email='" + email + '\'' + ", " +
        "createdAt=" + createdAt +
        ']';
  }
}