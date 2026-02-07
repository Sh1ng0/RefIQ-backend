library(refineR)
library(plumber)
library(jsonlite)

#* @post /calculate-ri
#* @param data_url URL de S3 (Presigned)
#* @param p_low Percentil inferior
#* @param p_high Percentil superior
#* @param test_code Código opcional para trazabilidad (Default: UNKNOWN)
function(res, data_url, p_low = 0.025, p_high = 0.975, test_code = "UNKNOWN") {

  # Log interno de R
  message(paste("[START] Processing request for:", test_code, "| URL:", data_url))

  tryCatch({

    # 1. LECTURA SEGURA:
    # Alineado con CanonicalCSV.java: Separador coma, decimal punto.
    # stringsAsFactors = FALSE es vital para no convertir textos a factores accidentalmente.
    Data <- try(read.csv(url(data_url), header = TRUE, sep = ",", dec = ".", stringsAsFactors = FALSE), silent = TRUE)

    if (inherits(Data, "try-error")) {
        message(paste("[ERROR] Fallo IO:", attr(Data, "condition")$message))
        res$status <- 400
        return(list(error = "No se pudo leer el CSV. Verifique URL o formato."))
    }

    # 2. VALIDACIÓN DEL CONTRATO (Por nombre, no por posición):
    # Verificamos que la columna 'value' exista.
    if (!"value" %in% colnames(Data)) {
        message("[ERROR] Contrato roto: No se encuentra la columna 'value' en el CSV.")
        res$status <- 422
        return(list(error = "El CSV canónico no cumple el contrato: Falta columna 'value'."))
    }

    # 3. EXTRACCIÓN Y LIMPIEZA:
    # Usamos el nombre de columna. R es inteligente manejando vectores.
    values <- as.numeric(Data$value)

    # Eliminamos NAs (Java envía "" para nulos, R los lee como NA en numéricos o vacíos, esto limpia ambos)
    values <- values[!is.na(values)]

    # Validación de cantidad mínima estadística
    if (length(values) < 10) {
      res$status <- 422
      return(list(error = paste("Datos insuficientes para RefineR. Válidos encontrados:", length(values))))
    }

    # Intentamos detectar la unidad del CSV si viene informada
    detected_unit <- "units"
    if ("unit" %in% colnames(Data)) {

       u_vals <- unique(Data$unit)
       u_vals <- u_vals[u_vals != "" & !is.na(u_vals)]
       if (length(u_vals) > 0) detected_unit <- u_vals[1]
    }

    # --- LÓGICA CORE DE REFINER ---
    calculated_value <- NULL
    ref_range_str <- NULL

    fit <- try(findRI(Data = values), silent = TRUE)

    if (!inherits(fit, "try-error")) {
        # Extracción de la media (mu)
        if (!is.null(fit$mu) && !is.na(fit$mu)) {
            calculated_value <- fit$mu
        }

        # Extracción del rango
        ris <- try(getRI(fit, RIperc = c(as.numeric(p_low), as.numeric(p_high))), silent = TRUE)
        if (!inherits(ris, "try-error") && !is.null(ris$PointEst)) {
             # Formateo "Low - High"
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

        # Manejo de Nulos explícito para JSON
        value = jsonlite::unbox(if(is.null(calculated_value)) NA else calculated_value),

        # Usamos la unidad detectada en el CSV
        unit = jsonlite::unbox(detected_unit),

        reference_range = jsonlite::unbox(ref_range_str),
        notes = jsonlite::unbox(note_message)
      ),
      parameters = list(
        p_low = jsonlite::unbox(as.numeric(p_low)),
        p_high = jsonlite::unbox(as.numeric(p_high)),
        n_samples = jsonlite::unbox(length(values)) # Info útil de debug
      )
    ))

  }, error = function(e) {
    message(paste("CRITICAL ERROR:", e$message))
    res$status <- 500
    return(list(error = paste("Error interno R:", e$message)))
  })
}