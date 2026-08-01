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
  @DisplayName("Debe devolver 200 OK con los datos del perfil si existe")
  void shouldReturn200AndProfileData() throws Exception {
    // GIVEN
    UUID myUserId = UUID.randomUUID();
    UserProfileResponse mockedResponse = new UserProfileResponse(
        myUserId, "Hospital Norte", "norte@refiq.com", Instant.now()
    );

    when(userService.getProfile(myUserId)).thenReturn(Optional.of(mockedResponse));

    // Creamos el principal exacto que tu JwtAuthenticationFilter genera en producción
    var authPrincipal = new UsernamePasswordAuthenticationToken(myUserId, null, Collections.emptyList());

    // WHEN & THEN
    mockMvc.perform(get("/api/users/profile")
            .with(authentication(authPrincipal))) // Inyectamos la identidad
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(myUserId.toString()))
        .andExpect(jsonPath("$.name").value("Hospital Norte"))
        .andExpect(jsonPath("$.contactEmail").value("norte@refiq.com"));
  }

  @Test
  @DisplayName("Debe devolver 404 Not Found si el perfil no existe")
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