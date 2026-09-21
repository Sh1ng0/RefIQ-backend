package com.refiq.platform.user.internal.service;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.logging.UserLogEvent;
import com.refiq.platform.user.internal.repository.DbUserProfileRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;


/**
 * Core service responsible for managing the read and write operations of the User module.
 * <p>
 * <b>Event-Driven Consumer:</b> This service acts as a subscriber in the platform's asynchronous
 * topology. It relies on Spring Modulith's {@code @ApplicationModuleListener} to guarantee the
 * transactional delivery of cross-module events, such as the initial profile creation triggered
 * by the Auth module.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final DbUserProfileRepository profileRepository;

  /**
   * Consumes the {@link UserRegisteredEvent} to provision a new user profile asynchronously.
   *
   * @param event The domain event emitted by the Authentication module.
   */
  @ApplicationModuleListener
  void on(UserRegisteredEvent event) {

    var newProfile = UserProfile.createNew(
        event.accountId(),
        event.userName(),
        event.contactEmail()
    );

    profileRepository.insert(newProfile);

    new UserLogEvent.ProfileCreated(newProfile.id(), newProfile.name()).log(log);
  }

  /**
   * Retrieves the user profile for display on the frontend.
   *
   * @param userId The UUID of the authenticated user.
   * @return An Optional containing the UserProfileResponse if found, or empty otherwise.
   */
  public Optional<UserProfileResponse> getProfile(UUID userId) {
    return profileRepository.findById(userId)
        .map(profile -> {
          new UserLogEvent.ProfileRetrieved(profile.id()).log(log);
          return new UserProfileResponse(
              profile.id(),
              profile.name(),
              profile.contactEmail(),
              profile.createdAt()
          );
        })
        .or(() -> {
          new UserLogEvent.ProfileNotFound(userId).log(log);
          return Optional.empty();
        });
  }
}