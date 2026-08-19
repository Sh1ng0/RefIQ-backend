package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.ingestion.api.event.IngestionFailedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Event listener responsible for tracking the lifecycle of asynchronous calculation processes.
 * <p>
 * Refactorizado a DOP. Actúa como el iniciador de la máquina de estados,
 * traduciendo eventos de dominio transversales en registros puramente inmutables de jOOQ.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CalculationTrackingListener {

  private static final Logger log = LoggerFactory.getLogger(CalculationTrackingListener.class);

  // Inyectamos el nuevo repositorio
  private final DbCalculationResultRepository repository;

  /**
   * Intercepts a successful file ingestion event and initializes the tracking mechanism.
   */
  @ApplicationModuleListener
  void on(FileAcceptedEvent event) {
    CalculationTrackingLogEvent.TRACKING_EVENT_RECEIVED.log(log, event.fileId());

    // 1. Instanciamos el estado inmutable (sin nulos, sin setters)
    var pendingState = new CalculationState.Pending(event.fileId());

    // 2. Inserción SQL explícita
    repository.insert(pendingState);

    CalculationTrackingLogEvent.TRACKING_RECORD_CREATED.log(log, event.fileId());
  }

  /**
   * Intercepts an ingestion failure event and acts as a compensating transaction.
   */
  @ApplicationModuleListener
  void on(IngestionFailedEvent event) {
    repository.findById(event.fileId()).ifPresent(currentState -> {

      // Creamos la caja hermética de fallo y mandamos la actualización exacta
      var failedState = new CalculationState.Failed(event.fileId(), event.errorMessage());

      repository.update(failedState);
    });
  }
}