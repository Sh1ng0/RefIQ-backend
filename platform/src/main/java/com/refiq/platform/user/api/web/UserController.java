package com.refiq.platform.user.api.web;

import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.service.UserService;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages user profile HTTP requests.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserService userService;

  /**
   * Retrieves the authenticated user's profile data.
   * <p>
   * The user's UUID is automatically extracted from the active security context.
   * </p>
   *
   * @param userId The UUID of the user, injected by Spring Security.
   * @return A {@link ResponseEntity} containing the profile data (200 OK) or a 404 Not Found if it does not exist.
   */
  @Override
  @GetMapping("/profile")
  public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal UUID userId) {

    return userService.getProfile(userId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}