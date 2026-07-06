package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import com.refiq.platform.ingestion.api.event.FileIngestedEvent;
import com.refiq.platform.ingestion.api.event.IngestionFailedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Event listener responsible for tracking the lifecycle of asynchronous calculation processes.
 * <p>
 * This service leverages Spring Modulith to intercept domain events published by the Ingestion module
 * in a completely decoupled manner. It acts as the initiator for the calculation tracking state machine,
 * managing the creation and early failure states of the {@link CalculationResultEntity} in the database.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CalculationTrackingListener {

  private static final Logger log = LoggerFactory.getLogger(CalculationTrackingListener.class);
  private final CalculationResultRepository repository;

  /**
   * Intercepts a successful file ingestion event and initializes the tracking mechanism.
   * <p>
   * By creating a database record with a {@code PENDING} status immediately after the file is ingested,
   * this method prevents race conditions. It ensures that when the Frontend immediately polls the
   * {@code ResultController} with the provided UUID, it receives a {@code 202 ACCEPTED} status
   * instead of a {@code 404 NOT FOUND}, even before the Data Lake or the R engine start their work.
   * </p>
   *
   * @param event The domain event containing the correlation ID (fileId) of the newly ingested file.
   */
  @ApplicationModuleListener
  void on(FileIngestedEvent event) {
    CalculationTrackingLogEvent.TRACKING_EVENT_RECEIVED.log(log, event.fileId());

    CalculationResultEntity trackingEntity = CalculationResultEntity.builder()
        .id(event.fileId())
        .status(CalculationStatus.PENDING)
        .build();

    repository.save(trackingEntity);

    CalculationTrackingLogEvent.TRACKING_RECORD_CREATED.log(log, event.fileId());
  }

  /**
   * Intercepts an ingestion failure event and acts as a compensating transaction.
   * <p>
   * If the asynchronous file upload to the storage layer (e.g., S3/MinIO) fails after the initial
   * acknowledgment, this listener catches the failure event and updates the tracking record to {@code FAILED}.
   * This is a critical fallback to prevent the Frontend from being stuck in an infinite polling loop.
   * </p>
   *
   * @param event The domain event detailing the failure reason and the associated fileId.
   */
  @ApplicationModuleListener
  void on(IngestionFailedEvent event) {
    repository.findById(event.fileId()).ifPresent(entity -> {
      entity.setStatus(CalculationStatus.FAILED);
      entity.setErrorMessage(event.errorMessage());
      repository.save(entity);
    });
  }
}