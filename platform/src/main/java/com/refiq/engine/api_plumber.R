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
  message(paste("Processing request for:", test_code, "| URL:", data_url))

  tryCatch({

    Data <- try(read.csv(url(data_url), header = TRUE, sep = ","), silent = TRUE)

    if (inherits(Data, "try-error")) {
        res$status <- 400
        return(list(error = "No se pudo leer el CSV. Verifique URL o formato."))
    }

    # 2. Limpieza y validación (Columna 5 fija)
    if (ncol(Data) < 5) {
        res$status <- 422
        return(list(error = "El CSV no tiene la estructura esperada (min 5 columnas)."))
    }

    values <- as.numeric(Data[, 5])
    values <- values[!is.na(values)]

    if (length(values) < 10) {
      res$status <- 422
      return(list(error = paste("Datos insuficientes. Se encontraron:", length(values))))
    }


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
    }


    note_message <- "No se pudo calcular nada"

        if (!is.null(ref_range_str)) {
            if (!is.null(calculated_value)) {
                note_message <- "Cálculo exitoso"
            } else {
                note_message <- "Cálculo parcial (Rango OK, sin valor estimado)"
            }
        } else {
            note_message <- "Cálculo no convergió"
        }

        return(list(
          lab_result = list(
            test_code = jsonlite::unbox(test_code),
            name = jsonlite::unbox("RefineR Analysis"),

            # Mantenemos el manejo de Nulos seguro que arreglamos antes
            value = jsonlite::unbox(if(is.null(calculated_value)) NA else calculated_value),

            unit = jsonlite::unbox("units"),

            reference_range = jsonlite::unbox(ref_range_str),

            # Usamos la nueva variable calculada arriba
            notes = jsonlite::unbox(note_message)
          ),
          parameters = list(
            p_low = jsonlite::unbox(as.numeric(p_low)),
            p_high = jsonlite::unbox(as.numeric(p_high))
          )
        ))

  }, error = function(e) {
    message(paste("CRITICAL ERROR:", e$message))
    res$status <- 500
    return(list(error = paste("Error interno R:", e$message)))
  })
}