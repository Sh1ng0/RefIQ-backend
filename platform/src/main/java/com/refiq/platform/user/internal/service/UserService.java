package com.refiq.platform.user.internal.service;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.user.api.dto.UserProfileResponse;

import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.repository.UserProfileRepository;


import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserProfileRepository profileRepository;

  /**
   * Catches the event emitted by the Auth module and creates the hospital profile.
   * By using @ApplicationModuleListener, Modulith guarantees the delivery of the event.
   *
   * @param event The domain event containing the new user's basic information.
   */
  @ApplicationModuleListener
  void on(UserRegisteredEvent event) {
    var newProfile = UserProfile.builder()
        .id(event.accountId()) // El UID hace de foreign key lógica
        .name(event.userName())
        .contactEmail(event.contactEmail())
        .build();

    profileRepository.save(newProfile);

    UserLogEvent.PROFILE_CREATED.log(log, newProfile.getId(), newProfile.getName());
  }

  /**
   * Retrieves the hospital profile to be displayed on the Frontend.
   *
   * @param userId The UUID of the authenticated user.
   * @return An Optional containing the UserProfileResponse if found, or empty otherwise.
   */
  @Transactional(readOnly = true)
  public Optional<UserProfileResponse> getProfile(UUID userId) {
    return profileRepository.findById(userId)
        .map(profile -> {
          UserLogEvent.PROFILE_RETRIEVED.log(log, profile.getId());
          return new UserProfileResponse(
              profile.getId(),
              profile.getName(),
              profile.getContactEmail(),
              profile.getCreatedAt()
          );
        })
        .or(() -> {
          UserLogEvent.PROFILE_NOT_FOUND.log(log, userId);
          return Optional.empty();
        });
  }
}