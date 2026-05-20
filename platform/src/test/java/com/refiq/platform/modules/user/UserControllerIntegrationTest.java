package com.refiq.platform.modules.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.user.internal.domain.UserProfile;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class UserControllerIntegrationTest extends UserBaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtProvider jwtProvider;

  @Test
  @DisplayName("GET /profile: 200 OK - Retorna perfil para usuario autenticado")
  void shouldReturnUserProfileWhenAuthenticated() throws Exception {
    // GIVEN
    UUID userId = UUID.randomUUID();
    var profile = UserProfile.builder()
        .id(userId)
        .name("Laboratorio de Test")
        .contactEmail("test@refiq.com")
        .build();
    userProfileRepository.save(profile);

    String token = jwtProvider.generateToken(userId);

    // WHEN & THEN
    mockMvc.perform(get("/api/users/profile")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Laboratorio de Test"))
        .andExpect(jsonPath("$.contactEmail").value("test@refiq.com"));
  }

  @Test
  @DisplayName("GET /profile: 404 NOT FOUND - Token válido pero perfil no creado")
  void shouldReturn404WhenProfileDoesNotExist() throws Exception {
    UUID nonExistentUserId = UUID.randomUUID();
    String token = jwtProvider.generateToken(nonExistentUserId);

    // WHEN & THEN
    mockMvc.perform(get("/api/users/profile")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /profile: 403 FORBIDDEN - Intento de acceso sin token")
  void shouldReturn403WhenNoTokenProvided() throws Exception {
    // WHEN & THEN
    // Spring Security debería bloquear esto antes de que llegue al controlador
    mockMvc.perform(get("/api/users/profile"))
        .andExpect(status().isForbidden());
  }
}