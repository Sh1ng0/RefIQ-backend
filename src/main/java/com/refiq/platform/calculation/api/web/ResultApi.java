package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.web.response.ResultWebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/results")
@Tag(name = "Result Module", description = "Endpoints to query the status and result of the asynchronous processing")
public interface ResultApi {

  @Operation(
      summary = "Query analysis result",
      description = "Allows the client to poll using the Correlation ID (fileId) to obtain the calculation status or the final result from the statistical engine."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Calculation successfully completed. Returns the reference intervals.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Success.class)
          )
      ),
      @ApiResponse(
          responseCode = "202",
          description = "Processing in progress (PENDING). The client should continue polling.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Success.class),
              examples = @ExampleObject(
                  name = "ProcessingExample",
                  value = "{\n  \"data\": {\n    \"status\": \"PENDING\",\n    \"message\": \"The file is being processed in the Data Lake or R engine\"\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "No tracking record found for the provided ID.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "NotFoundExample",
                  value = "{\n  \"error\": {\n    \"error\": \"No results found for the provided ID.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "422",
          description = "Business failure during calculation (e.g., invalid CSV, missing data).",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "DataInconsistencyExample",
                  value = "{\n  \"error\": {\n    \"error\": \"The canonical CSV does not meet the contract: Missing 'value' column.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "500",
          description = "Unrecoverable technical failure (The asynchronous process failed and will not retry).",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "EngineFailedExample",
                  value = "{\n  \"error\": {\n    \"error\": \"Internal analysis engine error after multiple attempts.\",\n    \"details\": null\n  }\n}"
              )
          )
      )
  })
  @GetMapping(value = "/{fileId}", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ResultWebResponse> getResult(
      @Parameter(description = "UUID of the file provided during ingestion", required = true)
      @PathVariable("fileId") UUID fileId
  );
}