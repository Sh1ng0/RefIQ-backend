package com.refiq.platform.ingestion.internal.service;


import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private final StoragePort storagePort;

  /**
   * Orquesta la subida del archivo al almacenamiento externo. No lanza excepciones, devuelve un
   * resultado sellado (DOP).
   */
  public IngestionResult ingest(IngestionFile file) {

    log.debug("Iniciando orquestación de ingesta para archivo: {} ({} bytes)", file.filename(),
        file.size());

    UUID fileId = UUID.randomUUID();

    // Ya pasaremos a String para s3 llegado el momento, lazy init
    String storageKey = fileId + "-" + file.filename();

    try {

      storagePort.upload(file, storageKey);

      IngestionLogEvent.UPLOAD_SUCCESS.log(log, fileId, storageKey);

      return new IngestionResult.Success(
          new IngestionResponse(fileId, Status.UPLOADED)
      );

    } catch (Exception e) {

      IngestionLogEvent.STORAGE_ERROR.log(log, fileId, e.getMessage());

      return new IngestionResult.StorageUnavailable(e.getMessage());
    }
  }


}



