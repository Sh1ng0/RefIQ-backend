library(refineR)
library(plumber)
library(jsonlite)
library(arrow)

#* @post /calculate-ri
#* @param data_url S3 URL (Presigned)
#* @param p_low Lower percentile
#* @param p_high Upper percentile
#* @param test_code Optional traceability code (Default: UNKNOWN)
function(res, data_url, p_low = 0.025, p_high = 0.975, test_code = "UNKNOWN") {

  message(paste("[START] Processing request for:", test_code, "| URL:", data_url))

  tryCatch({

    temp_file <- tempfile(fileext = ".parquet")
    dl_res <- try(download.file(url = data_url, destfile = temp_file, mode = "wb", quiet = TRUE), silent = TRUE)

    if (inherits(dl_res, "try-error") || dl_res != 0) {
        message("[ERROR] Network Failure: Could not download the file from S3.")
        res$status <- 400
        return(list(error = "Could not download the Parquet file. Check URL or expiration."))
    }

    Data <- try(arrow::read_parquet(temp_file), silent = TRUE)
    unlink(temp_file)

    if (inherits(Data, "try-error")) {
        message(paste("[ERROR] IO Failure:", attr(Data, "condition")$message))
        res$status <- 400
        return(list(error = "Could not read the Parquet file. Ensure the file is not corrupted."))
    }

    if ("analyte_value" %in% colnames(Data)) {
        Data$value <- Data$analyte_value
    }

    if (!"value" %in% colnames(Data)) {
        message("[ERROR] Broken contract: 'analyte_value' or 'value' column not found.")
        res$status <- 422
        return(list(error = "The Gold Parquet does not fulfill the contract: Missing 'analyte_value' column."))
    }

    values <- as.numeric(Data$value)
    values <- values[!is.na(values)]

    if (length(values) < 10) {
      res$status <- 422
      return(list(error = paste("Insufficient data for RefineR. Valid values found:", length(values))))
    }

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
         message(paste("[WARN] RefineR findRI failed:", attr(fit, "condition")$message))
    }

    note_message <- "Could not calculate anything"

    if (!is.null(ref_range_str)) {
        if (!is.null(calculated_value)) {
            note_message <- "Successful calculation"
        } else {
            note_message <- "Partial calculation (Range OK, no estimated value)"
        }
    } else {
        note_message <- "Calculation did not converge (Highly dispersed data or atypical distribution)"
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
    return(list(error = paste("Internal R error:", e$message)))
  })
}

#* @get /health
#* @serializer unboxedJSON
function() {
  list(status = "UP", service = "refiq-engine")
}