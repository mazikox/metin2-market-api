package com.mazikox.metin_market_api.ingestion;

import com.mazikox.metin_market_api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    private static final String VALID_TOKEN = "secret-scanner-token-123";

    private MockMvc mockMvc;

    @Mock
    private ImportService importService;

    @BeforeEach
    void setUp() {
        ImportController controller = new ImportController(importService, VALID_TOKEN);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
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
        when(importService.importBatch(any(ImportRequest.class)))
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
