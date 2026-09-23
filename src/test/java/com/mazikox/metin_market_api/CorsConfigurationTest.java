package com.mazikox.metin_market_api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigurationTest {

    private MockMvc mockMvc;

    @RestController
    static class DummyApiController {
        @GetMapping("/api/v1/test")
        public String apiGet() {
            return "ok";
        }

        @PostMapping("/api/v1/test")
        public String apiPost() {
            return "ok";
        }

        @GetMapping("/internal/v1/imports")
        public String internalGet() {
            return "internal";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(CorsConfiguration.class)
    static class TestConfig {
        @Bean
        public List<String> allowedOrigins() {
            return List.of("https://mazikox.pl", "https://*.mazikox.pl", "http://localhost:5173");
        }

        @Bean
        public CorsConfiguration corsConfiguration(List<String> allowedOrigins) {
            return new CorsConfiguration(allowedOrigins);
        }

        @Bean
        public DummyApiController dummyApiController() {
            return new DummyApiController();
        }
    }

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allowsGetFromTrustedOriginOnApi() throws Exception {
        mockMvc.perform(get("/api/v1/test")
                        .header("Origin", "https://mazikox.pl"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://mazikox.pl"));
    }

    @Test
    void allowsGetFromSubdomainOriginOnApi() throws Exception {
        mockMvc.perform(get("/api/v1/test")
                        .header("Origin", "https://pandora.mazikox.pl"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://pandora.mazikox.pl"));
    }

    @Test
    void rejectsUntrustedOriginOnApi() throws Exception {
        mockMvc.perform(get("/api/v1/test")
                        .header("Origin", "https://evil.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void internalEndpointsDoNotHaveCorsHeaders() throws Exception {
        mockMvc.perform(get("/internal/v1/imports")
                        .header("Origin", "https://mazikox.pl"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
