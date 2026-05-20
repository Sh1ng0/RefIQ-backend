package com.refiq.platform.user.api.web;



import com.refiq.platform.user.api.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/users")
@Tag(name = "User Module", description = "Endpoints para la consulta y gestión del perfil del hospital/usuario")
public interface UserApi {

  @Operation(
      summary = "Obtener el perfil del usuario autenticado",
      description = "Devuelve los datos del perfil (hospital) vinculados al token JWT actual.",
      security = { @SecurityRequirement(name = "bearerAuth") } // Opcional: si tienes configurado el botón de "Authorize" en Swagger
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Perfil recuperado exitosamente",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "No autorizado (Falta token o es inválido)",
          content = @Content(schema = @Schema(hidden = true))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Perfil no encontrado (Inconsistencia de datos)",
          content = @Content(schema = @Schema(hidden = true))
      )
  })
  @GetMapping("/profile")
  ResponseEntity<UserProfileResponse> getMyProfile(@Parameter(hidden = true) @AuthenticationPrincipal UUID userId);
}