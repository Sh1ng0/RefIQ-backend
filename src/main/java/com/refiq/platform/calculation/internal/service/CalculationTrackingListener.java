package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.logging.CalculationTrackingLogEvent;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.ingestion.api.event.IngestionFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Event listener responsible for tracking the lifecycle of asynchronous calculation processes.
 * <p>
 * Acts as the initiator of the state machine, translating cross-domain events into immutable state
 * records.
 * </p>
 */
@Service
public class CalculationTrackingListener {

  private static final Logger log = LoggerFactory.getLogger(CalculationTrackingListener.class);

  private final DbCalculationResultRepository repository;

  public CalculationTrackingListener(DbCalculationResultRepository repository) {
    this.repository = repository;
  }

  /**
   * Intercepts a successful file ingestion event and initializes the tracking mechanism.
   */
  @ApplicationModuleListener
  void on(FileAcceptedEvent event) {
    new CalculationTrackingLogEvent.TrackingEventReceived(event.fileId()).log(log);

    var pendingState = new CalculationState.Pending(event.fileId());

    repository.insert(pendingState);

    new CalculationTrackingLogEvent.TrackingRecordCreated(event.fileId()).log(log);
  }

  /**
   * Intercepts an ingestion failure event and acts as a compensating transaction.
   */
  @ApplicationModuleListener
  void on(IngestionFailedEvent event) {

    new CalculationTrackingLogEvent.IngestionFailedEventReceived(event.fileId(),
        event.errorMessage()).log(log);

    repository.findById(event.fileId()).ifPresent(currentState -> {

      var failedState = new CalculationState.Failed(event.fileId(), event.errorMessage());

      repository.update(failedState);

      new CalculationTrackingLogEvent.TrackingRecordUpdatedToFailed(event.fileId()).log(log);
    });
  }
}