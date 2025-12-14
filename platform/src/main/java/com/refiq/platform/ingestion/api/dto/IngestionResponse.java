package com.refiq.platform.ingestion.api.dto;


import java.util.UUID;

/**
 * Datos de confirmación tras una subida exitosa.
 *
 * @param fileId El identificador único (UUID) asignado al archivo en el sistema.
 * @param status Estado actual del archivo (ej: "UPLOADED", "PENDING_PROCESSING").
 */
public record IngestionResponse(UUID fileId, Status status) {

}
