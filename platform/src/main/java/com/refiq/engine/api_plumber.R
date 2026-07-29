library(refineR)
library(plumber)
library(jsonlite)
library(arrow) # NUEVA DEPENDENCIA: Necesaria para leer Parquet

#* @post /calculate-ri
#* @param data_url URL de S3 (Presigned)
#* @param p_low Percentil inferior
#* @param p_high Percentil superior
#* @param test_code Código opcional para trazabilidad (Default: UNKNOWN)
function(res, data_url, p_low = 0.025, p_high = 0.975, test_code = "UNKNOWN") {

  message(paste("[START] Processing request for:", test_code, "| URL:", data_url))

  tryCatch({

    # 1. LECTURA SEGURA DEL PARQUET (Vía archivo temporal):
    # Usamos download.file a un temporal porque Arrow a veces requiere
    # compilaciones específicas de C++ para leer presigned URLs HTTP directamente.
    # Esto es a prueba de balas.
    temp_file <- tempfile(fileext = ".parquet")
    dl_res <- try(download.file(url = data_url, destfile = temp_file, mode = "wb", quiet = TRUE), silent = TRUE)

    if (inherits(dl_res, "try-error") || dl_res != 0) {
        message("[ERROR] Fallo Red: No se pudo descargar el archivo desde S3.")
        res$status <- 400
        return(list(error = "No se pudo descargar el archivo Parquet. Verifique URL o expiración."))
    }

    Data <- try(arrow::read_parquet(temp_file), silent = TRUE)
    unlink(temp_file) # Limpiamos el disco inmediatamente

    if (inherits(Data, "try-error")) {
        message(paste("[ERROR] Fallo IO:", attr(Data, "condition")$message))
        res$status <- 400
        return(list(error = "No se pudo leer el Parquet. Verifique que el archivo no esté corrupto."))
    }

    # 2. VALIDACIÓN Y ADAPTACIÓN DEL CONTRATO:
    # El Data Lake genera la columna 'analyte_value', pero nosotros usábamos 'value'.
    # Hacemos un alias al vuelo para mantener tu lógica core de R intacta.
    if ("analyte_value" %in% colnames(Data)) {
        Data$value <- Data$analyte_value
    }

    if (!"value" %in% colnames(Data)) {
        message("[ERROR] Contrato roto: No se encuentra la columna 'analyte_value' o 'value'.")
        res$status <- 422
        return(list(error = "El Parquet Gold no cumple el contrato: Falta columna 'analyte_value'."))
    }

    # 3. EXTRACCIÓN Y LIMPIEZA:
    values <- as.numeric(Data$value)
    values <- values[!is.na(values)]

    if (length(values) < 10) {
      res$status <- 422
      return(list(error = paste("Datos insuficientes para RefineR. Válidos encontrados:", length(values))))
    }

    # Detección de unidad: El Data Lake la llama 'analyte_UNIT', mantenemos 'unit' por retrocompatibilidad
    detected_unit <- "units"
    if ("analyte_UNIT" %in% colnames(Data)) {
       u_vals <- unique(Data$analyte_UNIT)
       u_vals <- u_vals[!is.na(u_vals) & u_vals != ""]
       if (length(u_vals) > 0) detected_unit <- u_vals[1]
    } else if ("unit" %in% colnames(Data)) {
       u_vals <- unique(Data$unit)
       u_vals <- u_vals[!is.na(u_vals) & u_vals != ""]
       if (length(u_vals) > 0) detected_unit <- u_vals[1]
    }

    # --- LÓGICA CORE DE REFINER ---
    calculated_value <- NULL
    ref_range_str <- NULL

    fit <- try(findRI(Data = values), silent = TRUE)

    if (!inherits(fit, "try-error")) {
        if (!is.null(fit$mu) && !is.na(fit$mu)) {
            calculated_value <- fit$mu
        }

        ris <- try(getRI(fit, RIperc = c(as.numeric(p_low), as.numeric(p_high))), silent = TRUE)
        if (!inherits(ris, "try-error") && !is.null(ris$PointEst)) {
             ref_range_str <- paste(round(ris$PointEst, 2), collapse = " - ")
        }
    } else {
         message(paste("[WARN] RefineR findRI falló:", attr(fit, "condition")$message))
    }

    # 4. RESPUESTA
    note_message <- "No se pudo calcular nada"

    if (!is.null(ref_range_str)) {
        if (!is.null(calculated_value)) {
            note_message <- "Cálculo exitoso"
        } else {
            note_message <- "Cálculo parcial (Rango OK, sin valor estimado)"
        }
    } else {
        note_message <- "Cálculo no convergió (Datos muy dispersos o distribución atípica)"
    }

    return(list(
      lab_result = list(
        test_code = jsonlite::unbox(test_code),
        name = jsonlite::unbox("RefineR Analysis"),
        value = jsonlite::unbox(if(is.null(calculated_value)) NA else calculated_value),
        unit = jsonlite::unbox(detected_unit),
        reference_range = jsonlite::unbox(ref_range_str),
        notes = jsonlite::unbox(note_message)
      ),
      parameters = list(
        p_low = jsonlite::unbox(as.numeric(p_low)),
        p_high = jsonlite::unbox(as.numeric(p_high)),
        n_samples = jsonlite::unbox(length(values))
      )
    ))

  }, error = function(e) {
    message(paste("CRITICAL ERROR:", e$message))
    res$status <- 500
    return(list(error = paste("Error interno R:", e$message)))
  })
}

#* @get /health
#* @serializer unboxedJSON
function() {
  list(status = "UP", service = "refiq-engine")
}