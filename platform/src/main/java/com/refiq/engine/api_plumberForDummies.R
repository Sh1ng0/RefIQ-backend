# ### JAVA: imports. Similar a `import com.paquete.Libreria;`
library(refineR)  # Librería de estadística (Tu lógica de negocio)
library(plumber)  # Framework Web (Tu Spring Boot)
library(jsonlite) # Manejo de JSON (Tu Jackson/Gson)

# -----------------------------------------------------------------------------
# DEFINICIÓN DEL ENDPOINT
# -----------------------------------------------------------------------------
# ### JAVA: Anotaciones. Aunque parecen comentarios, Plumber los lee (Son metadatos, se inyectan al ejecutar el build del Dockerfile)
# Equivalente a: @PostMapping("/calculate-ri")
#* @post /calculate-ri

# ### JAVA: @RequestParam o @RequestBody. Plumber hace el binding automático.
#* @param data_url URL de S3 (Presigned)
#* @param p_low Percentil inferior
#* @param p_high Percentil superior
#* @param test_code Código opcional para trazabilidad (Default: UNKNOWN)

# -----------------------------------------------------------------------------
# CONTROLADOR (FUNCTION)
# -----------------------------------------------------------------------------
# ### JAVA: public ResponseEntity<Map<String, Object>> calculate(...) { ... }
# 'res' se inyecta automáticamente (como HttpServletResponse) para controlar status codes.
# Los valores por defecto (p_low = 0.025) funcionan igual que en Java.
function(res, data_url, p_low = 0.025, p_high = 0.975, test_code = "UNKNOWN") {

  # ### JAVA: Logger.info(...). Imprime en la consola de Docker.
  message(paste("Processing request for:", test_code, "| URL:", data_url))

  # ---------------------------------------------------------------------------
  # MANEJO DE ERRORES GLOBAL (CATCH-ALL)
  # ---------------------------------------------------------------------------
  # ### JAVA: bloque try-catch clásico para evitar que el servidor se caiga (Error 500).
  tryCatch({

    # ### R NOTE: 'try()' aquí es diferente al 'tryCatch' de arriba.
    # NO detiene la ejecución si falla. Devuelve un objeto especial si hay error.
    # ### JAVA: Es como un "Soft Try" o un patrón Either<Error, Data>.
    Data <- try(read.csv(url(data_url), header = TRUE, sep = ","), silent = TRUE)

    # ### JAVA: if (Data instanceof Exception) { ... }
    # Verificamos si la lectura falló sin usar un catch explícito.
    if (inherits(Data, "try-error")) {
        res$status <- 400 # ### JAVA: response.setStatus(400);
        return(list(error = "No se pudo leer el CSV. Verifique URL o formato."))
    }

    # 2. Limpieza y validación
    # ### R NOTE: 'ncol' cuenta columnas.
    if (ncol(Data) < 5) {
        res$status <- 422 # Unprocessable Entity
        return(list(error = "El CSV no tiene la estructura esperada (min 5 columnas)."))
    }

    # ### R NOTE: Data[, 5] -> Todas las filas, columna 5.
    # ### JAVA: OJO! Los índices en R empiezan en 1. Esto sería data.getColumn(4) en Java.
    values <- as.numeric(Data[, 5])
    values <- values[!is.na(values)] # ### JAVA: stream().filter(Objects::nonNull)

    if (length(values) < 10) {
      res$status <- 422
      return(list(error = paste("Datos insuficientes. Se encontraron:", length(values))))
    }

    # Inicializamos variables (como null en Java)
    calculated_value <- NULL
    ref_range_str <- NULL

    # Lógica de negocio (Librería refineR) protegido con 'try' soft
    fit <- try(findRI(Data = values), silent = TRUE)

    if (!inherits(fit, "try-error")) {
        # Extracción de la media (mu)
        # ### R NOTE: '$' es el operador de acceso a miembros (como el '.' en Java).
        if (!is.null(fit$mu) && !is.na(fit$mu)) {
            calculated_value <- fit$mu
        }

        # Extracción del rango
        # ### R NOTE: 'c(...)' crea un vector/array. c(0.025, 0.975)
        ris <- try(getRI(fit, RIperc = c(as.numeric(p_low), as.numeric(p_high))), silent = TRUE)

        if (!inherits(ris, "try-error") && !is.null(ris$PointEst)) {
             # Formateo "Low - High"
             # ### JAVA: String.join(" - ", ...)
             ref_range_str <- paste(round(ris$PointEst, 2), collapse = " - ")
        }
    }

    # -------------------------------------------------------------------------
    # RESPUESTA JSON (SERIALIZACIÓN)
    # -------------------------------------------------------------------------
    # ### R NOTE: R trata todo como vectores (arrays). 5 es [5].
    # ### JAVA: Si devuelves esto tal cual, Jackson crearía: "value": [5.0]
    # 'jsonlite::unbox' fuerza a que sea un escalar: "value": 5.0
    return(list(
      lab_result = list(
        test_code = jsonlite::unbox(test_code),
        name = jsonlite::unbox("RefineR Analysis"),

        # Operador ternario de R: if(cond) A else B (Igual que Java ? :)
        value = jsonlite::unbox(if(is.null(calculated_value)) NA else calculated_value),

        unit = jsonlite::unbox("units"),

        reference_range = jsonlite::unbox(ref_range_str),

        notes = jsonlite::unbox(ifelse(is.null(calculated_value),
                                       "Cálculo no convergió",
                                       "Cálculo exitoso"))
      ),
      parameters = list(
        p_low = jsonlite::unbox(as.numeric(p_low)),
        p_high = jsonlite::unbox(as.numeric(p_high))
      )
    )) # ### JAVA: Al devolver una 'list', Plumber la serializa a JSON automáticamente.

  }, error = function(e) {
    # -------------------------------------------------------------------------
    # EXCEPTION HANDLER (Error 500)
    # -------------------------------------------------------------------------
    # Esto se ejecuta si hay un crash real (NullPointer, Syntax Error, etc)
    message(paste("CRITICAL ERROR:", e$message))
    res$status <- 500
    return(list(error = paste("Error interno R:", e$message)))
  })
}