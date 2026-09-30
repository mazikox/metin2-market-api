package com.mazikox.metin_market_api.ingestion;

import com.mazikox.metin_market_api.shared.web.ApiExceptionHandler;
import com.mazikox.metin_market_api.ingestion.api.ImportController;
import com.mazikox.metin_market_api.ingestion.api.ImportRequest;
import com.mazikox.metin_market_api.ingestion.api.ImportResponse;
import com.mazikox.metin_market_api.ingestion.application.ImportBatch;
import com.mazikox.metin_market_api.server.infrastructure.ServerRoutingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    private static final String VALID_TOKEN = "secret-scanner-token-123";
    private static final String ELDER_TOKEN = "elder-scanner-token-456";
    private static final String BEAVIUM_TOKEN = "beavium-scanner-token-789";

    private MockMvc mockMvc;

    @Mock
    private ImportBatch importBatch;

    @BeforeEach
    void setUp() {
        ImportController controller = new ImportController(importBatch, VALID_TOKEN, ELDER_TOKEN, BEAVIUM_TOKEN);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new ServerRoutingFilter())
                .build();
    }

    @Test
    void importBatchWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/v1/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "test",
                                  "batchId": "b1",
                                  "runs": [],
                                  "observations": []
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importBatchWithInvalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/v1/imports")
                        .header("X-Scanner-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "test",
                                  "batchId": "b1",
                                  "runs": [],
                                  "observations": []
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importBatchWithValidTokenReturnsOk() throws Exception {
        when(importBatch.importBatch(any(ImportRequest.class)))
                .thenReturn(new ImportResponse("test", "b1", false, 1, 1, 1));

        mockMvc.perform(post("/internal/v1/imports")
                        .header("X-Scanner-Token", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "test",
                                  "batchId": "b1",
                                  "runs": [],
                                  "observations": []
                                }
                                """))
                .andExpect(status().isOk());
    }
}
