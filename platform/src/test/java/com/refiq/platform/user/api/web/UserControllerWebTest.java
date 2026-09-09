package com.refiq.platform.user.api.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.support.slices.BaseWebWithAuthTest;
import com.refiq.platform.user.api.dto.UserProfileResponse;
import com.refiq.platform.user.internal.service.UserService;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(UserController.class)
@DisplayName("User - Web Layer (Isolated)")
class UserControllerWebTest extends BaseWebWithAuthTest {

  @MockitoBean
  private UserService userService;

  @Test
  @DisplayName("Should return 200 OK with profile data if it exists")
  void shouldReturn200AndProfileData() throws Exception {
    // GIVEN
    UUID myUserId = UUID.randomUUID();
    UserProfileResponse mockedResponse = new UserProfileResponse(
        myUserId, "North Hospital", "north@refiq.com", Instant.now()
    );

    when(userService.getProfile(myUserId)).thenReturn(Optional.of(mockedResponse));

    var authPrincipal = new UsernamePasswordAuthenticationToken(myUserId, null, Collections.emptyList());

    // WHEN & THEN
    mockMvc.perform(get("/api/users/profile")
            .with(authentication(authPrincipal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(myUserId.toString()))
        .andExpect(jsonPath("$.name").value("North Hospital"))
        .andExpect(jsonPath("$.contactEmail").value("north@refiq.com"));
  }

  @Test
  @DisplayName("Should return 404 Not Found if the profile is missing")
  void shouldReturn404WhenProfileIsMissing() throws Exception {
    // GIVEN
    UUID ghostUserId = UUID.randomUUID();
    when(userService.getProfile(ghostUserId)).thenReturn(Optional.empty());

    var authPrincipal = new UsernamePasswordAuthenticationToken(ghostUserId, null, Collections.emptyList());

    // WHEN & THEN
    mockMvc.perform(get("/api/users/profile")
            .with(authentication(authPrincipal)))
        .andExpect(status().isNotFound());
  }
}