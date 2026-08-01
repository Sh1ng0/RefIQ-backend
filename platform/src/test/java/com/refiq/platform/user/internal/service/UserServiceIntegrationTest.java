package com.refiq.platform.user.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.support.slices.BasePostgresTest;
import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.repository.UserProfileRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest
@ActiveProfiles("test")
@DisplayName("User - Service & Events (Postgres Integration)")
class UserServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private UserService userService;

  @Autowired
  private UserProfileRepository userProfileRepository;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @AfterEach
  void cleanUp() {
    // Limpiamos a mano porque el test de eventos requiere suspender el @Transactional global
    userProfileRepository.deleteAll();
  }

  @Test
  @DisplayName("Event Listener: Debe crear el perfil de forma asíncrona al recibir UserRegisteredEvent")
  @Transactional(propagation = Propagation.NOT_SUPPORTED) // Obligatorio para que Modulith Outbox dispare el evento
  void shouldCreateProfileAsynchronously() {
    // GIVEN
    UUID accountId = UUID.randomUUID();
    var event = new UserRegisteredEvent(accountId, "Laboratorio BioTest", "bio@test.com");

    // WHEN: Publicamos el evento envuelto en una transacción real
    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(event);
    });

    // THEN: Esperamos a que Modulith lo procese en background y lo guarde en DB
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> {
          var savedProfile = userProfileRepository.findById(accountId);
          assertThat(savedProfile).isPresent();
          assertThat(savedProfile.get().getName()).isEqualTo("Laboratorio BioTest");
          assertThat(savedProfile.get().getContactEmail()).isEqualTo("bio@test.com");
        });
  }

  // TODO estamos aquí

  @Test
  @DisplayName("getProfile: Debe recuperar el DTO correcto desde Postgres")
  void shouldRetrieveProfileProperly() {
    // GIVEN
    UUID accountId = UUID.randomUUID();

    // 1. Preparamos el estado de la base de datos DIRECTAMENTE,
    // sin pasar por la mensajería asíncrona.
    UserProfile profile = UserProfile.builder()
        .id(accountId)
        .name("Lab Sur")
        .contactEmail("sur@test.com")
        // createdAt se autogenera por el @PrePersist
        .build();

    userProfileRepository.save(profile);

    // WHEN
    Optional<UserProfileResponse> response = userService.getProfile(accountId);

    // THEN
    assertThat(response).isPresent();
    assertThat(response.get().name()).isEqualTo("Lab Sur");
    assertThat(response.get().contactEmail()).isEqualTo("sur@test.com");
    assertThat(response.get().joinedAt()).isNotNull();
  }
}