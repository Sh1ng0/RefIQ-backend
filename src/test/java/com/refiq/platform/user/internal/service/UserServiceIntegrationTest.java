package com.refiq.platform.user.internal.service;

import static com.refiq.platform.shared.db.generated.Tables.REFIQ_USER_PROFILES;
import static org.assertj.core.api.Assertions.assertThat;
import static com.refiq.platform.shared.db.generated.Tables.REFIQ_CREDENTIALS;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.support.slices.BasePostgresTest;
import com.refiq.platform.support.slices.RefiqModuleTest;
import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.repository.DbUserProfileRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.Scenario;
import org.springframework.transaction.support.TransactionTemplate;

@RefiqModuleTest
@DisplayName("User - Service & Events (Postgres Integration)")
class UserServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private UserService userService;

  @Autowired
  private DbUserProfileRepository userProfileRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private DSLContext dsl;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    dsl.deleteFrom(REFIQ_USER_PROFILES).execute();
    dsl.deleteFrom(REFIQ_CREDENTIALS).execute();
    dsl.execute("TRUNCATE TABLE event_publication");
  }

  @Test
  @DisplayName("Event Listener: Should create the profile asynchronously upon receiving UserRegisteredEvent")
  void shouldCreateProfileAsynchronously(Scenario scenario) {
    // GIVEN
    UUID accountId = UUID.randomUUID();
    insertDummyCredential(accountId, "bio@test.com");

    var event = new UserRegisteredEvent(accountId, "BioTest Laboratory", "bio@test.com");

    // WHEN & THEN
    scenario.publish(event)
        .andWaitForStateChange(() -> userProfileRepository.findById(accountId).orElse(null))
        .andVerify(savedProfile -> {
          assertThat(savedProfile).isNotNull();
          assertThat(savedProfile.name()).isEqualTo("BioTest Laboratory");
          assertThat(savedProfile.contactEmail()).isEqualTo("bio@test.com");
        });
  }

  @Test
  @DisplayName("getProfile: Should retrieve the correct DTO from Postgres")
  void shouldRetrieveProfileProperly() {
    // GIVEN
    UUID accountId = UUID.randomUUID();
    insertDummyCredential(accountId, "south@test.com");

    UserProfile profile = new UserProfile(
        accountId,
        "South Lab",
        "south@test.com",
        Instant.now()
    );

    userProfileRepository.insert(profile);

    // WHEN
    Optional<UserProfileResponse> response = userService.getProfile(accountId);

    // THEN
    assertThat(response).isPresent();
    assertThat(response.get().name()).isEqualTo("South Lab");
    assertThat(response.get().contactEmail()).isEqualTo("south@test.com");
    assertThat(response.get().joinedAt()).isNotNull();
  }

  /**
   * Helper to inject raw infrastructure.
   * <p>
   * Since the class lacks @Transactional, this performs an instantaneous and natural commit,
   * avoiding ghost data scenarios between threads.
   * </p>
   */
  private void insertDummyCredential(UUID id, String email) {
    dsl.insertInto(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.ID, id)
        .set(REFIQ_CREDENTIALS.EMAIL, email)
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, "dummy_hash")
        .set(REFIQ_CREDENTIALS.CREATED_AT, Instant.now().atOffset(ZoneOffset.UTC))
        .execute();
  }
}