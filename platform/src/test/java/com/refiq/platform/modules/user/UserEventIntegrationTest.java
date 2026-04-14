package com.refiq.platform.modules.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

class UserEventIntegrationTest extends UserBaseIntegrationTest {

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("Event Listener: Debe crear el perfil de forma asíncrona al recibir UserRegisteredEvent")
  void shouldCreateProfileAsynchronously() {
    // GIVEN
    UUID accountId = UUID.randomUUID();
    var event = new UserRegisteredEvent(accountId, "Laboratorio BioTest", "bio@test.com");

    // WHEN
    // Simulamos la transacción atómica que ocurre en tu AuthService.
    // Al terminar este bloque lambda, Spring hace el COMMIT,
    // y Modulith dispara el Outbox y el Listener.
    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(event);
    });
    // THEN: Awaitility espera a que el Listener de User haga su trabajo
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
}