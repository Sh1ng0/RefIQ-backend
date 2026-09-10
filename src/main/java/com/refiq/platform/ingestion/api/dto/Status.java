package com.refiq.platform.ingestion.api.dto;

/**
 * Defines the state machine for the asynchronous file ingestion process.
 * <p>
 * Utilized by the web layer to notify the frontend regarding the current status
 * of a submitted file as it moves through the pipeline.
 * </p>
 */
public enum Status {

  /**
   * Indicates that the file has been fully uploaded and processed by the system.
   */
  UPLOADED,

  /**
   * Indicates that the file has been ingested and is currently awaiting analytical results from the Data Lake.
   */
  PENDING_RESULT,

  /**
   * Indicates that the file has been accepted and is undergoing asynchronous processing (e.g., streaming to S3).
   */
  PENDING_PROCESSING
}