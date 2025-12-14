package com.refiq.platform.ingestion.internal.port;


import com.refiq.platform.ingestion.internal.domain.IngestionFile;

/**
 * Puerto de salida (Output Port) para la persistencia de archivos.
 * <p>
 * Define QUÉ se hace (guardar), pero no CÓMO (S3, disco local, FTP).
 */
public interface StoragePort {


  /**
   * Sube el contenido del archivo al sistema de almacenamiento.
   *
   * @param file El objeto de dominio con el stream y metadatos.
   * @param uniqueKey La clave única (ej. UUID) con la que se guardará.
   * @return El identificador o ruta final en el storage (ej. S3 Key o ETag).
   */
  String upload(IngestionFile file, String uniqueKey);

}
