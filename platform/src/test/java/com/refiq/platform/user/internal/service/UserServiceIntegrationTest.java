package com.refiq.platform.user.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.support.slices.BasePostgresTest;
import com.refiq.platform.support.slices.RefiqModuleTest;
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
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RefiqModuleTest
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

    userProfileRepository.deleteAll();
  }

  @Test
  @DisplayName("Event Listener: Debe crear el perfil de forma asíncrona al recibir UserRegisteredEvent")
  void shouldCreateProfileAsynchronously(Scenario scenario) {
    // GIVEN
    UUID accountId = UUID.randomUUID();
    var event = new UserRegisteredEvent(accountId, "Laboratorio BioTest", "bio@test.com");

    // WHEN & THEN
    scenario.publish(event)

        .andWaitForStateChange(() -> userProfileRepository.findById(accountId).orElse(null))

        .andVerify(savedProfile -> {
          assertThat(savedProfile).isNotNull();
          assertThat(savedProfile.getName()).isEqualTo("Laboratorio BioTest");
          assertThat(savedProfile.getContactEmail()).isEqualTo("bio@test.com");
        });
  }


  @Test
  @DisplayName("getProfile: Debe recuperar el DTO correcto desde Postgres")
  void shouldRetrieveProfileProperly() {
    // GIVEN
    UUID accountId = UUID.randomUUID();

    UserProfile profile = UserProfile.builder()
        .id(accountId)
        .name("Lab Sur")
        .contactEmail("sur@test.com")
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