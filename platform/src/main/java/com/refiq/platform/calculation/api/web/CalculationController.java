package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calculations")
@RequiredArgsConstructor
public class CalculationController {

  private final CalculationService calculationService;

  @PostMapping("/run")
  public ResponseEntity<?> runAnalysis(@RequestBody CalculationRequest request) {
    CalculationResult result = calculationService.runAnalysis(request);

    return switch (result) {
      case CalculationResult.Success s ->
          ResponseEntity.ok(s.response());

      case CalculationResult.InvalidRequest ir ->
          ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ir.reason());

      case CalculationResult.EngineUnavailable eu ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(eu.debugInfo());

      case CalculationResult.DataInconsistency di ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(di.details());
    };
  }
}