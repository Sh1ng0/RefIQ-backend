package com.refiq.platform.calculation.internal.domain;

import java.util.UUID;

/**
 * Pure domain representation of the calculation state machine using Sealed Interfaces.
 * Eliminates nulls completely by ensuring each state only holds relevant data.
 */
public sealed interface CalculationState {

  UUID id();


  record Pending(UUID id) implements CalculationState {}


  record Processing(UUID id) implements CalculationState {}


  record Success(UUID id, String payload) implements CalculationState {}


  record Failed(UUID id, String errorMessage) implements CalculationState {}
}