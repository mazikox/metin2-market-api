package com.mazikox.metin_market_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketApiIntegrationTest {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    static { postgres.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.scanner-token", () -> "test-token");
    }

    @Autowired ObjectMapper objectMapper;
    @Value("${local.server.port}") int port;

    @Test
    void importsRetriesWithoutDuplicatesAndSearchesWithShopDetails() throws Exception {
        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest importRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/imports"))
                .header("X-Scanner-Token", "test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        JsonNode first = objectMapper.readTree(http.send(importRequest, HttpResponse.BodyHandlers.ofString()).body());
        JsonNode retry = objectMapper.readTree(http.send(importRequest, HttpResponse.BodyHandlers.ofString()).body());

        assertThat(first.path("alreadyProcessed").asBoolean()).isFalse();
        assertThat(first.path("importedListings").asInt()).isEqualTo(1);
        assertThat(retry.path("alreadyProcessed").asBoolean()).isTrue();

        HttpResponse<String> search = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/items?query=Zatruty")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(search.statusCode()).isEqualTo(200);
        JsonNode searchBody = objectMapper.readTree(search.body());
        JsonNode item = searchBody.path("items").get(0);
        assertThat(item.path("price").asLong()).isEqualTo(20_000_000_000L);
        assertThat(item.path("attributes")).hasSize(5);
        assertThat(item.path("sockets")).hasSize(6);
        assertThat(item.path("shop").path("vid").asLong()).isEqualTo(47844);
        assertThat(item.path("shop").path("mapId").asText()).isEqualTo("metin2_map_a1_summer");
        assertThat(item.path("observedAt").asText()).startsWith("2026-09-12T09:34:18.155");
        assertThat(searchBody.path("totalElements").asLong()).isEqualTo(1);
    }
}
