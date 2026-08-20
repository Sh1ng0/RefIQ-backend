package com.refiq.platform.user.internal.service;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.repository.DbUserProfileRepository; // Repositorio actualizado

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

  // Inyectamos el nuevo repositorio Data-Oriented
  private final DbUserProfileRepository profileRepository;

  /**
   * Core service responsible for managing the read and write operations of the User module.
   * <p>
   * <b>Event-Driven Consumer:</b> This service acts as a subscriber in the platform's asynchronous
   * topology. It relies on Spring Modulith's {@code @ApplicationModuleListener} to guarantee the
   * transactional delivery of cross-module events, such as the initial profile creation triggered
   * by the Auth module.
   * </p>
   */
  @ApplicationModuleListener
  void on(UserRegisteredEvent event) {
    // Usamos el factory method del Record, el dominio dicta su estado inicial
    var newProfile = UserProfile.createNew(
        event.accountId(),
        event.userName(),
        event.contactEmail()
    );

    // Inserción explícita en lugar de save()
    profileRepository.insert(newProfile);

    // Accesores nativos del Record (.id(), .name())
    UserLogEvent.PROFILE_CREATED.log(log, newProfile.id(), newProfile.name());
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
          UserLogEvent.PROFILE_RETRIEVED.log(log, profile.id());
          return new UserProfileResponse(
              profile.id(),
              profile.name(),
              profile.contactEmail(),
              profile.createdAt()
          );
        })
        .or(() -> {
          UserLogEvent.PROFILE_NOT_FOUND.log(log, userId);
          return Optional.empty();
        });
  }
}