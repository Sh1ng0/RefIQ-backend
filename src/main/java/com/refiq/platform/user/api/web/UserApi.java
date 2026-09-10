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
@Tag(name = "User Module", description = "Endpoints for retrieving and managing the user/hospital profile")
public interface UserApi {

  @Operation(
      summary = "Retrieves the authenticated user's profile",
      description = "Returns the profile (hospital) data linked to the current JWT token.",
      security = { @SecurityRequirement(name = "bearerAuth") }
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Profile successfully retrieved",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileResponse.class))
      ),
      @ApiResponse(
          responseCode = "401",
          description = "Unauthorized (Missing or invalid token)",
          content = @Content(schema = @Schema(hidden = true))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Profile not found (Data inconsistency)",
          content = @Content(schema = @Schema(hidden = true))
      )
  })
  @GetMapping("/profile")
  ResponseEntity<UserProfileResponse> getMyProfile(@Parameter(hidden = true) @AuthenticationPrincipal UUID userId);
}