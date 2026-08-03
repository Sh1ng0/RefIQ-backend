package com.refiq.platform.ingestion.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.support.slices.BaseWebWithAuthTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(IngestionController.class)
@DisplayName("Ingestion - Web Layer (Isolated)")
class IngestionControllerWebTest extends BaseWebWithAuthTest {

  @MockitoBean
  private IngestionService ingestionService;

  private static final String UPLOAD_URL = "/api/ingestion/upload";

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 400 Bad Request si el archivo está vacío")
  void shouldReturn400WhenFileIsEmpty() throws Exception {
    // GIVEN
    MockMultipartFile emptyFile = new MockMultipartFile(
        "file", "empty.csv", MediaType.TEXT_PLAIN_VALUE, new byte[0]
    );

    // WHEN & THEN
    mockMvc.perform(multipart(UPLOAD_URL)
            .file(emptyFile)
            .param("analyte", "TSH"))
        .andExpect(status().isBadRequest())

        .andExpect(jsonPath("$.error.error").value("El archivo está vacío."));

    verifyNoInteractions(ingestionService);
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 400 Bad Request si el analito no está soportado")
  void shouldReturn400WhenAnalyteIsUnsupported() throws Exception {
    // GIVEN
    MockMultipartFile validFile = new MockMultipartFile(
        "file", "data.csv", MediaType.TEXT_PLAIN_VALUE, "dummy content".getBytes()
    );

    // WHEN & THEN
    mockMvc.perform(multipart(UPLOAD_URL)
            .file(validFile)
            .param("analyte", "INVALID_ANALYTE"))
        .andExpect(status().isBadRequest())

        .andExpect(jsonPath("$.error.error").value("Analito no soportado: INVALID_ANALYTE"));

    verifyNoInteractions(ingestionService);
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 202 Accepted cuando el archivo es válido y se inicia el procesamiento")
  void shouldReturn202WhenValidFileIsProcessed() throws Exception {
    // GIVEN
    MockMultipartFile validFile = new MockMultipartFile(
        "file", "tsh_data.csv", "text/csv", "id,value\n1,2.5".getBytes()
    );
    UUID expectedFileId = UUID.randomUUID();

    IngestionResponse response = new IngestionResponse(expectedFileId, Status.PENDING_PROCESSING);

    when(ingestionService.ingest(any(IngestionFile.class)))
        .thenReturn(new IngestionResult.Success(response));

    // WHEN & THEN
    mockMvc.perform(multipart(UPLOAD_URL)
            .file(validFile)
            .param("analyte", "TSH"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.fileId").value(expectedFileId.toString()))
        .andExpect(jsonPath("$.data.status").value("PENDING_PROCESSING"));

    verify(ingestionService).ingest(any(IngestionFile.class));
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 503 Service Unavailable si el almacenamiento falla")
  void shouldReturn503WhenStorageIsUnavailable() throws Exception {
    // GIVEN
    MockMultipartFile validFile = new MockMultipartFile(
        "file", "tsh_data.csv", "text/csv", "id,value\n1,2.5".getBytes()
    );

    when(ingestionService.ingest(any(IngestionFile.class)))
        .thenReturn(new IngestionResult.StorageUnavailable("S3 Timeout Connection Exception"));

    // WHEN & THEN
    mockMvc.perform(multipart(UPLOAD_URL)
            .file(validFile)
            .param("analyte", "TSH"))
        .andExpect(status().isServiceUnavailable())

        .andExpect(jsonPath("$.error.error").value("Servicio no disponible"))
        .andExpect(jsonPath("$.error.details.debug").value("S3 Timeout Connection Exception"));
  }
}