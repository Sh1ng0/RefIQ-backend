package com.refiq.platform.ingestion.api.dto;

/**
 * Representa el resultado exhaustivo (sealed) de la operación de ingesta de archivos.
 * <p>
 * Modela el flujo de éxito o fallo sin recurrir a excepciones de control de flujo.
 */
public sealed interface IngestionResult {

  /**
   * El archivo se ha subido correctamente al almacenamiento (S3).
   */
  record Success(IngestionResponse response) implements IngestionResult {}

  /**
   * El archivo enviado no es válido (vacío, nombre incorrecto, etc.).
   * Esto evita excepciones como IllegalArgumentException en la lógica de negocio.
   */
  record InvalidFile(String filename, String reason) implements IngestionResult {}

  /**
   * Fallo técnico al intentar guardar el archivo (S3 caído, timeout, error de I/O).
   * Permite al controlador decidir si reintentar o devolver un 503.
   */
  record StorageUnavailable(String debugInfo) implements IngestionResult {}
}