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

    @Test
    void suggestsUpgradeFamiliesAggregatesIdenticalOffersAndCalculatesShopPriceStatistics() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        JsonNode imported = importPayload(http, """
                {
                  "sourceId": "market-presentation-test",
                  "batchId": "market-presentation-test-batch",
                  "runs": [],
                  "observations": [
                    {
                      "observationId": "presentation-shop-a", "shopVid": 9001, "shopTitle": "A",
                      "ownerName": "Tester", "mapId": "test", "channel": 1, "x": 1, "y": 1, "z": 0,
                      "observedAt": "2026-09-12T10:00:00Z", "itemCount": 7, "contentFingerprint": "presentation-a",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 9009, "itemName": "Miecz testowy+9", "count": 1, "priceRaw": 300, "unitPrice": 300, "tailField": 0, "attributes": [{"slotIndex": 0, "attrType": 17, "attrValue": 10}], "sockets": [{"socketIndex": 0, "socketValue": 1}]},
                        {"listingId": 2, "slotIndex": 1, "vnum": 9009, "itemName": "Miecz testowy+9", "count": 1, "priceRaw": 300, "unitPrice": 300, "tailField": 0, "attributes": [{"slotIndex": 0, "attrType": 17, "attrValue": 10}], "sockets": [{"socketIndex": 0, "socketValue": 1}]},
                        {"listingId": 3, "slotIndex": 2, "vnum": 9009, "itemName": "Miecz testowy+9", "count": 1, "priceRaw": 300, "unitPrice": 300, "tailField": 0, "attributes": [{"slotIndex": 0, "attrType": 17, "attrValue": 11}], "sockets": [{"socketIndex": 0, "socketValue": 1}]},
                        {"listingId": 4, "slotIndex": 3, "vnum": 9009, "itemName": "Miecz testowy+9", "count": 1, "priceRaw": 300, "unitPrice": 300, "tailField": 0, "attributes": [{"slotIndex": 0, "attrType": 17, "attrValue": 10}], "sockets": [{"socketIndex": 0, "socketValue": 2}]},
                        {"listingId": 5, "slotIndex": 4, "vnum": 9000, "itemName": "Miecz testowy+0", "count": 1, "priceRaw": 500, "unitPrice": 500, "tailField": 0, "attributes": [], "sockets": []},
                        {"listingId": 6, "slotIndex": 5, "vnum": 9010, "itemName": "Przedmiot z plusem+", "count": 1, "priceRaw": 1, "unitPrice": 1, "tailField": 0, "attributes": [], "sockets": []},
                        {"listingId": 7, "slotIndex": 6, "vnum": 777, "itemName": "Statystyczny przedmiot", "count": 1, "priceRaw": 30, "unitPrice": 30, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "presentation-shop-b", "shopVid": 9001, "shopTitle": "B",
                      "ownerName": "Tester", "mapId": "test", "channel": 1, "x": 2, "y": 2, "z": 0,
                      "observedAt": "2026-09-12T10:01:00Z", "itemCount": 1, "contentFingerprint": "presentation-b",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 777, "itemName": "Statystyczny przedmiot", "count": 1, "priceRaw": 32, "unitPrice": 32, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "presentation-shop-c", "shopVid": 9002, "shopTitle": "C",
                      "ownerName": "Tester", "mapId": "test", "channel": 1, "x": 3, "y": 3, "z": 0,
                      "observedAt": "2026-09-12T10:02:00Z", "itemCount": 2, "contentFingerprint": "presentation-c",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 777, "itemName": "Statystyczny przedmiot", "count": 1, "priceRaw": 40, "unitPrice": 40, "tailField": 0, "attributes": [], "sockets": []},
                        {"listingId": 2, "slotIndex": 1, "vnum": 777, "itemName": "Statystyczny przedmiot", "count": 1, "priceRaw": 45, "unitPrice": 45, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);
        assertThat(imported.path("importedObservations").asInt()).isEqualTo(3);
        assertThat(imported.path("importedListings").asInt()).isEqualTo(10);

        JsonNode search = getJson(http, "/api/v1/items?vnum=9009&size=20");
        assertThat(search.path("totalElements").asLong()).isEqualTo(3);
        JsonNode mergedItem = null;
        for (JsonNode item : search.path("items")) {
            if (item.path("listingCount").asInt() == 2) {
                mergedItem = item;
                break;
            }
        }
        assertThat(mergedItem).isNotNull();
        assertThat(mergedItem.path("totalQuantity").asLong()).isEqualTo(2);
        assertThat(mergedItem.path("totalPrice").asLong()).isEqualTo(600);

        JsonNode familySearch = getJson(http, "/api/v1/items?vnum=9000&vnum=9009&size=20");
        assertThat(familySearch.path("totalElements").asLong()).isEqualTo(4);
        assertThat(familySearch.path("items")).allSatisfy(item ->
                assertThat(item.path("vnum").asInt()).isIn(9000, 9009));

        JsonNode suggestions = getJson(http, "/api/v1/items/suggestions?query=Miecz%20testowy");
        assertThat(suggestions.path("totalMatches").asLong()).isEqualTo(2);
        assertThat(suggestions.path("suggestions").toString()).contains("UPGRADE_FAMILY");
        assertThat(suggestions.path("suggestions").toString()).contains("Miecz testowy +0-9");
        JsonNode barePlusSuggestions = getJson(http, "/api/v1/items/suggestions?query=Przedmiot%20z%20plusem");
        assertThat(barePlusSuggestions.path("suggestions").toString()).doesNotContain("UPGRADE_FAMILY");

        HttpResponse<String> cors = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/items/suggestions?query=Miecz")).header("Origin", "https://mazikox.pl").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(cors.headers().firstValue("Access-Control-Allow-Origin")).contains("https://mazikox.pl");

        HttpResponse<String> subdomainCors = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/items/suggestions?query=Miecz")).header("Origin", "https://pandora.mazikox.pl").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(subdomainCors.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://pandora.mazikox.pl");

        HttpResponse<String> untrustedCors = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/items/suggestions?query=Miecz")).header("Origin", "https://untrusted.example").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(untrustedCors.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

        HttpResponse<String> importCors = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/internal/v1/imports")).header("Origin", "https://mazikox.pl").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(importCors.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

        JsonNode statistics = getJson(http, "/api/v1/items/statistics?vnum=777").path("items").get(0);
        assertThat(statistics.path("minimumPrice").asLong()).isEqualTo(30);
        assertThat(statistics.path("meanPrice").decimalValue()).isEqualByComparingTo("34");
        assertThat(statistics.path("medianPrice").decimalValue()).isEqualByComparingTo("32");
        assertThat(statistics.path("contributingShopCount").asLong()).isEqualTo(3);
        assertThat(statistics.path("rawOfferCount").asLong()).isEqualTo(4);

        JsonNode familyStatistics = getJson(http, "/api/v1/items/statistics?vnum=777&vnum=9000").path("items");
        assertThat(familyStatistics).hasSize(2);
        JsonNode upgradeZeroStatistics = null;
        for (JsonNode item : familyStatistics) {
            if (item.path("vnum").asInt() == 9000) {
                upgradeZeroStatistics = item;
                break;
            }
        }
        assertThat(upgradeZeroStatistics).isNotNull();
        assertThat(upgradeZeroStatistics.path("minimumPrice").asLong()).isEqualTo(500);
        assertThat(upgradeZeroStatistics.path("meanPrice").decimalValue()).isEqualByComparingTo("500");
    }

    private JsonNode importPayload(HttpClient http, String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/imports"))
                .header("X-Scanner-Token", "test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode getJson(HttpClient http, String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
