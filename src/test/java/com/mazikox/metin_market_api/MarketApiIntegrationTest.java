package com.mazikox.metin_market_api;

import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
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
        registry.add("app.scanner-token.pandora", () -> "test-token");
        registry.add("app.scanner-token.elder", () -> "elder-test-token");
        registry.add("app.scanner-token.beavium", () -> "beavium-test-token");
    }

    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Value("${local.server.port}") int port;

    @BeforeEach
    void cleanDatabase() {
        ServerContext.withServer(GameServer.PANDORA, () -> jdbc.sql("""
                TRUNCATE pandora.shop_listing_socket, pandora.shop_listing_attribute,
                         pandora.shop_listing, pandora.shop_observation, pandora.scan_run,
                         pandora.synchronization_batch,
                         elder.shop_listing_socket, elder.shop_listing_attribute,
                         elder.shop_listing, elder.shop_observation, elder.scan_run,
                         elder.synchronization_batch,
                         beavium.shop_listing_socket, beavium.shop_listing_attribute,
                         beavium.shop_listing, beavium.shop_observation, beavium.scan_run,
                         beavium.synchronization_batch,
                         pandora.item_definition, elder.item_definition, beavium.item_definition CASCADE
                """).update());
    }

    private static final String PROTO_HEADER = "vnum\tname\ttype\tsubtype\tsize\tanti\treq_level\tdef\tmin_atk\tmax_atk\tmin_matk\tmax_matk\tsockets\tapp1_t\tapp1_v\tapp2_t\tapp2_v\tapp3_t\tapp3_v\n";

    private HttpResponse<String> importProto(HttpClient http, String server, String token, String rows) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/internal/v1/servers/" + server + "/imports/item-proto"))
                .header("X-Scanner-Token", token)
                .header("Content-Type", "text/tab-separated-values; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(PROTO_HEADER + rows)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void catalogEnrichesExistingOffersAndImportsAreIsolatedValidatedAndRepeatable() throws Exception {
        var http = HttpClient.newHttpClient();
        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        assertThat(postImport(http, "/internal/v1/servers/beavium/imports", "beavium-test-token", body).statusCode()).isEqualTo(200);
        String row = "180\tZatruty Miecz+0\t1\t0\t3\t262176\t75\t0\t100\t140\t0\t0\t3\t7\t20\t0\t0\t0\t0\n";
        assertThat(importProto(http, "beavium", "test-token", row).statusCode()).isEqualTo(401);
        assertThat(importProto(http, "unknown", "beavium-test-token", row).statusCode()).isEqualTo(404);
        assertThat(importProto(http, "beavium", "beavium-test-token", row).statusCode()).isEqualTo(200);
        assertThat(importProto(http, "beavium", "beavium-test-token", row).statusCode()).isEqualTo(200);
        assertThat(getJson(http, "/api/v1/servers/beavium/items?vnum=180").path("items").get(0).path("metadata").path("requiredLevel").asInt()).isEqualTo(75);
        assertThat(getJson(http, "/api/v1/servers/beavium/items?vnum=180").path("items").get(0).path("metadata").path("builtInBonuses")).hasSize(1);
        ServerContext.withServer(GameServer.PANDORA, () -> {
            assertThat(jdbc.sql("SELECT count(*) FROM beavium.item_definition").query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("SELECT count(*) FROM elder.item_definition").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM pandora.item_definition").query(Long.class).single()).isZero();
            return null;
        });
        var baseBonus = getJson(http, "/api/v1/servers/beavium/items?vnum=180")
                .path("items").get(0).path("metadata").path("builtInBonuses").get(0);
        assertThat(baseBonus.path("name").asText()).isEqualTo("Szybkość Ataku");
        assertThat(baseBonus.path("displayValue").asText()).isEqualTo("+20%");
        // A malformed later row must not update an otherwise valid earlier definition.
        String revised = row.replace("\t75\t", "\t80\t");
        assertThat(importProto(http, "beavium", "beavium-test-token", revised + "bad row").statusCode()).isEqualTo(400);
        assertThat(importProto(http, "beavium", "beavium-test-token", row + row).statusCode()).isEqualTo(400);
        assertThat(getJson(http, "/api/v1/servers/beavium/items?vnum=180").path("items").get(0).path("metadata").path("requiredLevel").asInt()).isEqualTo(75);
        // Remove a former base bonus on an update, while leaving scanned attributes intact.
        assertThat(importProto(http, "beavium", "beavium-test-token", revised.replace("\t7\t20\t", "\t0\t0\t")).statusCode()).isEqualTo(200);
        var item = getJson(http, "/api/v1/servers/beavium/items?vnum=180").path("items").get(0);
        assertThat(item.path("metadata").path("requiredLevel").asInt()).isEqualTo(80);
        assertThat(item.path("metadata").path("builtInBonuses")).isEmpty();
        assertThat(item.path("attributes")).hasSize(5);
        // Catalog imports tolerate and report the known arrow range without discarding other items.
        var arrow = importProto(http, "beavium", "beavium-test-token",
                "8000\tStrzała\t1\t6\t1\t52\t0\t0\t1\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\n");
        assertThat(arrow.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(arrow.body()).path("unusualAttackRangeVnums").get(0).asInt()).isEqualTo(8000);
        assertThat(getJson(http, "/api/v1/servers/beavium/items?vnum=180").path("items").get(0).path("metadata").path("requiredLevel").asInt()).isEqualTo(80);
    }

    @Test
    void extraBonusFiltersCoverWholeMarketUseAndOnOneOfferAndIgnoreBaseProperties() throws Exception {
        var http = HttpClient.newHttpClient();
        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        assertThat(postImport(http, "/internal/v1/servers/beavium/imports", "beavium-test-token", body).statusCode()).isEqualTo(200);
        assertThat(importProto(http, "beavium", "beavium-test-token",
                "180\tZatruty Miecz+0\t1\t0\t3\t262176\t75\t0\t100\t140\t0\t0\t3\t7\t20\t0\t0\t0\t0\n").statusCode()).isEqualTo(200);
        ServerContext.withServer(GameServer.BEAVIUM, () -> {
            jdbc.sql("""
                    INSERT INTO shop_listing (observation_id, source_listing_id, slot_index, item_vnum,
                        item_name, quantity, price_raw, unit_price)
                    SELECT id, v.source_id, v.slot, v.vnum, 'Miecz testowy', 1, v.price, v.price
                    FROM shop_observation CROSS JOIN (VALUES
                        (2, 1, 181, 10), (3, 2, 182, 20), (4, 3, 183, 30),
                        (5, 4, 184, 40), (6, 5, 183, 30)) v(source_id, slot, vnum, price)
                    """).update();
            jdbc.sql("""
                    INSERT INTO shop_listing_attribute (listing_id, slot_index, attr_type, attr_value)
                    SELECT l.id, a.slot, a.type, a.value FROM shop_listing l JOIN (VALUES
                        (181, 0, 72, 10), (182, 0, 71, -20), (183, 0, 72, 45),
                        (183, 1, 71, -20), (184, 0, 72, 50), (184, 1, 71, -25)) a(vnum, slot, type, value)
                        ON a.vnum = l.item_vnum
                    """).update();
            return null;
        });
        String path = "/api/v1/servers/beavium/items";
        var page0 = getJson(http, path + "?bonus=72:40&size=1");
        assertThat(page0.path("totalElements").asInt()).isEqualTo(3);
        assertThat(page0.path("items").get(0).path("vnum").asInt()).isEqualTo(183);
        assertThat(page0.path("items").get(0).path("listingCount").asInt()).isEqualTo(2);
        assertThat(getJson(http, path + "?bonus=72:40&size=1&page=2").path("items").get(0).path("vnum").asInt()).isEqualTo(180);
        var outOfRange = getJson(http, path + "?bonus=72:40&size=1&page=10");
        assertThat(outOfRange.path("items")).isEmpty();
        assertThat(outOfRange.path("totalElements").asInt()).isEqualTo(3);
        assertThat(getJson(http, path + "?bonus=72:40&sort=priceDesc").path("items").get(0).path("vnum").asInt()).isEqualTo(180);
        var both = getJson(http, path + "?bonus=72:40&bonus=71:-22");
        assertThat(both.path("totalElements").asInt()).isEqualTo(1);
        assertThat(both.path("items").get(0).path("vnum").asInt()).isEqualTo(183);
        // The two conditions appear on different offers in one shop, never on the same offer.
        assertThat(getJson(http, path + "?vnum=181&vnum=182&bonus=72&bonus=71").path("totalElements").asInt()).isZero();
        assertThat(getJson(http, path + "?bonus=71").path("totalElements").asInt()).isEqualTo(4);
        assertThat(getJson(http, path + "?bonus=71:0").path("totalElements").asInt()).isZero();
        assertThat(getJson(http, path + "?query=Zatruty&bonus=72:40").path("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson(http, path + "?vnum=181&bonus=72:40").path("totalElements").asInt()).isZero();
        // Item 180 has a built-in attack speed bonus, which must not satisfy an extra-bonus filter.
        assertThat(getJson(http, path + "?bonus=7").path("totalElements").asInt()).isZero();
        assertThat(getJson(http, "/api/v1/servers/elder/items?bonus=72:40").path("totalElements").asInt()).isZero();
        var options = getJson(http, path + "/bonus-options");
        assertThat(options).extracting(o -> o.path("type").asInt()).contains(72, 71).doesNotContain(7);
        for (var option : options) {
            if (option.path("type").asInt() == 72) {
                assertThat(option.path("name").asText()).isEqualTo("Średnie Obrażenia");
                assertThat(option.path("unit").asText()).isEqualTo("%");
            }
        }
        assertThat(getJson(http, "/api/v1/servers/elder/items/bonus-options")).isEmpty();
    }

    private String protoRow(int vnum, String name, int type, int subtype, int level) {
        String[] columns = new String[19];
        java.util.Arrays.fill(columns, "0");
        columns[0] = Integer.toString(vnum); columns[1] = name;
        columns[2] = Integer.toString(type); columns[3] = Integer.toString(subtype);
        columns[4] = "1"; columns[6] = Integer.toString(level);
        return String.join("\t", columns) + "\n";
    }

    @Test
    void categoryAndLevelFiltersCombineWithBonusesBeforePagingAndExcludeUnknownMetadata() throws Exception {
        var http = HttpClient.newHttpClient();
        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        assertThat(postImport(http, "/internal/v1/servers/beavium/imports", "beavium-test-token", body).statusCode()).isEqualTo(200);
        assertThat(importProto(http, "beavium", "beavium-test-token",
                protoRow(180, "Miecz", 1, 0, 75) + protoRow(181, "Naszyjnik A", 2, 5, 30)
                + protoRow(182, "Naszyjnik B", 2, 5, 75) + protoRow(183, "Bransoleta", 2, 3, 0)
                + protoRow(8000, "Strzała", 1, 6, 0)).statusCode()).isEqualTo(200);
        ServerContext.withServer(GameServer.BEAVIUM, () -> {
            jdbc.sql("""
                    INSERT INTO shop_listing (observation_id, source_listing_id, slot_index, item_vnum,
                        item_name, quantity, price_raw, unit_price)
                    SELECT id, v.source_id, v.slot, v.vnum, v.name, 1, v.price, v.price
                    FROM shop_observation CROSS JOIN (VALUES
                        (2, 1, 181, 'Naszyjnik A', 10), (3, 2, 182, 'Naszyjnik B', 20),
                        (4, 3, 183, 'Bransoleta', 30), (5, 4, 184, 'Bez metadanych', 40),
                        (6, 5, 8000, 'Strzała', 1)) v(source_id, slot, vnum, name, price)
                    """).update();
            jdbc.sql("""
                    INSERT INTO shop_listing_attribute (listing_id, slot_index, attr_type, attr_value)
                    SELECT id, 0, 1, CASE item_vnum WHEN 181 THEN 1500 ELSE 500 END
                    FROM shop_listing WHERE item_vnum IN (181, 182)
                    """).update();
            return null;
        });
        String path = "/api/v1/servers/beavium/items";
        var first = getJson(http, path + "?category=necklaces&maxLevel=75&size=1");
        assertThat(first.path("totalElements").asInt()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("vnum").asInt()).isEqualTo(181);
        assertThat(getJson(http, path + "?category=necklaces&maxLevel=75&size=1&page=1").path("items").get(0).path("vnum").asInt()).isEqualTo(182);
        var missingPage = getJson(http, path + "?category=necklaces&minLevel=30&maxLevel=75&size=1&page=9");
        assertThat(missingPage.path("totalElements").asInt()).isEqualTo(2);
        assertThat(missingPage.path("items")).isEmpty();
        assertThat(getJson(http, path + "?category=necklaces&minLevel=75&maxLevel=75").path("items").get(0).path("vnum").asInt()).isEqualTo(182);
        var combined = getJson(http, path + "?category=necklaces&maxLevel=75&bonus=1:1000");
        assertThat(combined.path("totalElements").asInt()).isEqualTo(1);
        assertThat(combined.path("items").get(0).path("vnum").asInt()).isEqualTo(181);
        assertThat(getJson(http, path + "?category=necklaces&vnum=180").path("totalElements").asInt()).isZero();
        assertThat(getJson(http, path + "?category=necklaces&query=Naszyjnik&sort=priceDesc").path("items").get(0).path("vnum").asInt()).isEqualTo(182);
        assertThat(getJson(http, path + "?category=weapons").path("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson(http, path + "?category=swords&minLevel=75").path("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson(http, path + "?maxLevel=0").path("totalElements").asInt()).isEqualTo(2);
        assertThat(getJson(http, path + "?maxLevel=75").path("totalElements").asInt()).isEqualTo(5);
        assertThat(getJson(http, path).path("totalElements").asInt()).isEqualTo(6);
        assertThat(getJson(http, "/api/v1/servers/elder/items?maxLevel=75").path("totalElements").asInt()).isZero();
        var options = getJson(http, path + "/category-options");
        assertThat(options.path("available").asBoolean()).isTrue();
        assertThat(options.path("categories")).extracting(c -> c.path("value").asText()).contains("weapons", "necklaces", "bracelets").doesNotContain("helmets");
        assertThat(getJson(http, "/api/v1/servers/elder/items/category-options").path("available").asBoolean()).isFalse();
    }

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
        assertThat(item.path("observedAt").asText()).isEqualTo("2026-09-12");
        assertThat(searchBody.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void serverRoutesKeepPandoraOutputAndIsolateAllThreeServers() throws Exception {
        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();

        HttpResponse<String> pandoraImport = postImport(http, "/internal/v1/imports", "test-token", body);
        assertThat(pandoraImport.statusCode()).isEqualTo(200);
        JsonNode legacyPandora = getJson(http, "/api/v1/items?query=Zatruty");
        JsonNode routedPandora = getJson(http, "/api/v1/servers/pandora/items?query=Zatruty");
        assertThat(routedPandora).isEqualTo(legacyPandora);
        assertThat(legacyPandora.path("totalElements").asInt()).isEqualTo(1);

        JsonNode emptyElder = getJson(http, "/api/v1/servers/elder/items?query=Zatruty");
        assertThat(emptyElder.path("totalElements").asInt()).isZero();

        HttpResponse<String> wrongElderToken = postImport(
                http, "/internal/v1/servers/elder/imports", "test-token", body);
        assertThat(wrongElderToken.statusCode()).isEqualTo(401);
        HttpResponse<String> elderImport = postImport(
                http, "/internal/v1/servers/elder/imports", "elder-test-token", body);
        assertThat(elderImport.statusCode()).isEqualTo(200);

        HttpResponse<String> beaviumImport = postImport(
                http, "/internal/v1/servers/beavium/imports", "beavium-test-token", body);
        assertThat(beaviumImport.statusCode()).isEqualTo(200);

        JsonNode elderSearch = getJson(http, "/api/v1/servers/elder/items?query=Zatruty");
        JsonNode beaviumSearch = getJson(http, "/api/v1/servers/beavium/items?query=Zatruty");
        assertThat(elderSearch.path("totalElements").asInt()).isEqualTo(1);
        assertThat(beaviumSearch.path("totalElements").asInt()).isEqualTo(1);
        assertThat(elderSearch.path("items").get(0).path("attributes").get(0).path("code").asText())
                .isEqualTo("UNKNOWN");
        assertThat(getJson(http, "/api/v1/items?query=Zatruty")).isEqualTo(legacyPandora);

        HttpResponse<String> unknownServer = http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/v1/servers/unknown/items?query=Zatruty")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(unknownServer.statusCode()).isEqualTo(404);
    }

    @Test
    void suggestsUpgradeFamiliesAggregatesIdenticalOffersAndCalculatesShopPriceStatistics() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        JsonNode imported = importPayload(http, """
                {
                  "sourceId": "market-presentation-test",
                  "batchId": "market-presentation-test-batch",
                  "runs": [
                    {
                      "runId": "presentation-run-1",
                      "startedAt": "2026-09-12T10:00:00Z",
                      "endedAt": "2026-09-12T10:05:00Z",
                      "state": 3,
                      "mapId": "test",
                      "channel": 1,
                      "totalTargets": 10,
                      "visitedTargets": 3,
                      "failedTargets": 0,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "presentation-shop-a", "runId": "presentation-run-1", "shopVid": 9001, "shopTitle": "A",
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
                      "observationId": "presentation-shop-b", "runId": "presentation-run-1", "shopVid": 9003, "shopTitle": "B",
                      "ownerName": "Tester", "mapId": "test", "channel": 1, "x": 2, "y": 2, "z": 0,
                      "observedAt": "2026-09-12T10:01:00Z", "itemCount": 1, "contentFingerprint": "presentation-b",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 777, "itemName": "Statystyczny przedmiot", "count": 1, "priceRaw": 32, "unitPrice": 32, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "presentation-shop-c", "runId": "presentation-run-1", "shopVid": 9002, "shopTitle": "C",
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

        JsonNode overview = getJson(http, "/api/v1/items/overview");
        assertThat(overview.path("observedShopCount").asLong()).isEqualTo(3);
        assertThat(overview.path("scanEndedAt").asText()).isEqualTo("2026-09-12T10:05:00Z");
        assertThat(overview.path("items")).extracting(item -> item.path("vnum").asInt())
                .containsExactly(777, 9000, 9009, 9010);
        assertThat(overview.path("items").get(0).path("shopCount").asLong()).isEqualTo(3);
        assertThat(overview.path("items").get(0).path("totalQuantity").asLong()).isEqualTo(4);
        assertThat(overview.path("items").get(0).path("minimumPrice").asLong()).isEqualTo(30);
        // Multiple slots and different bonuses in one shop still count as one shop.
        assertThat(overview.path("items").get(2).path("shopCount").asLong()).isEqualTo(1);
        assertThat(overview.path("items").get(2).path("totalQuantity").asLong()).isEqualTo(4);
        assertThat(getJson(http, "/api/v1/items/overview?limit=2").path("items")).hasSize(2);
        assertThat(getJson(http, "/api/v1/servers/pandora/items/overview")).isEqualTo(overview);
        // Ranking by units must happen before limiting, across all items in the scan.
        JsonNode quantityOverview = getJson(http, "/api/v1/items/overview?sort=quantity&limit=2");
        assertThat(quantityOverview.path("items")).extracting(item -> item.path("vnum").asInt())
                .containsExactly(777, 9009);
        assertThat(quantityOverview.path("items").get(1).path("totalQuantity").asLong()).isEqualTo(4);
        assertThat(quantityOverview.path("items").get(1).path("shopCount").asLong()).isEqualTo(1);
        assertThat(getJson(http, "/api/v1/servers/pandora/items/overview?sort=quantity&limit=2"))
                .isEqualTo(quantityOverview);

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

        JsonNode unaccentSuggestions = getJson(http, "/api/v1/items/suggestions?query=miecz%20TESTOWY");
        assertThat(unaccentSuggestions.path("totalMatches").asLong()).isEqualTo(2);

        JsonNode unaccentSearch = getJson(http, "/api/v1/items?query=miecz%20testowy");
        assertThat(unaccentSearch.path("totalElements").asLong()).isEqualTo(4);

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
        assertThat(statistics.path("trimmedMeanPrice").decimalValue()).isEqualByComparingTo("34");
        assertThat(statistics.path("contributingShopCount").asLong()).isEqualTo(3);
        assertThat(statistics.path("rawOfferCount").asLong()).isEqualTo(4);
        assertThat(statistics.path("percentiles").path("p20").asLong()).isEqualTo(30);
        assertThat(statistics.path("percentiles").path("p50").asLong()).isEqualTo(32);
        assertThat(statistics.path("percentiles").path("p75").asLong()).isEqualTo(40);
        assertThat(statistics.path("buyerReference").path("price").asLong()).isEqualTo(30);
        assertThat(statistics.path("buyerReference").path("shopsAtOrBelow").asLong()).isEqualTo(1);
        assertThat(statistics.path("buyerReference").path("quantityAtOrBelow").asLong()).isEqualTo(1);
        assertThat(statistics.path("depth")).isNotEmpty();
        assertThat(statistics.path("histogram")).isNotEmpty();

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

    @Test
    void publicMarketReflectsOnlyLatestCompletedPublishableRun() throws Exception {
        HttpClient http = HttpClient.newHttpClient();

        // 1. Older full completed run (Run A)
        importPayload(http, """
                {
                  "sourceId": "multi-run-test",
                  "batchId": "batch-run-a",
                  "runs": [
                    {
                      "runId": "run-a-full-old",
                      "startedAt": "2026-09-13T10:00:00Z",
                      "endedAt": "2026-09-13T10:10:00Z",
                      "state": 3,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 100,
                      "visitedTargets": 100,
                      "failedTargets": 0,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-a-1", "runId": "run-a-full-old", "shopVid": 101, "shopTitle": "Shop A1",
                      "ownerName": "OwnerA1", "mapId": "map-1", "channel": 1, "x": 10, "y": 10, "z": 0,
                      "observedAt": "2026-09-13T10:02:00Z", "itemCount": 1, "contentFingerprint": "fp-a-1",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 1000, "itemName": "Stary Przedmiot A", "count": 1, "priceRaw": 1000, "unitPrice": 1000, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-a-2", "runId": "run-a-full-old", "shopVid": 102, "shopTitle": "Shop A2",
                      "ownerName": "OwnerA2", "mapId": "map-1", "channel": 1, "x": 20, "y": 20, "z": 0,
                      "observedAt": "2026-09-13T10:04:00Z", "itemCount": 1, "contentFingerprint": "fp-a-2",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 2000, "itemName": "Stary Przedmiot B", "count": 1, "priceRaw": 2000, "unitPrice": 2000, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        // 2. Newer full completed run (Run B)
        importPayload(http, """
                {
                  "sourceId": "multi-run-test",
                  "batchId": "batch-run-b",
                  "runs": [
                    {
                      "runId": "run-b-full-new",
                      "startedAt": "2026-09-13T12:00:00Z",
                      "endedAt": "2026-09-13T12:10:00Z",
                      "state": 3,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 100,
                      "visitedTargets": 100,
                      "failedTargets": 0,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-b-1", "runId": "run-b-full-new", "shopVid": 201, "shopTitle": "Shop B1",
                      "ownerName": "OwnerB1", "mapId": "map-1", "channel": 1, "x": 30, "y": 30, "z": 0,
                      "observedAt": "2026-09-13T12:02:00Z", "itemCount": 1, "contentFingerprint": "fp-b-1",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 1000, "itemName": "Stary Przedmiot A", "count": 1, "priceRaw": 800, "unitPrice": 800, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-b-2", "runId": "run-b-full-new", "shopVid": 202, "shopTitle": "Shop B2",
                      "ownerName": "OwnerB2", "mapId": "map-1", "channel": 1, "x": 40, "y": 40, "z": 0,
                      "observedAt": "2026-09-13T12:04:00Z", "itemCount": 1, "contentFingerprint": "fp-b-2",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 3000, "itemName": "Nowy Przedmiot C", "count": 1, "priceRaw": 5000, "unitPrice": 5000, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-b-3", "runId": "run-b-full-new", "shopVid": 203, "shopTitle": "Shop B3",
                      "ownerName": "OwnerB3", "mapId": "map-1", "channel": 1, "x": 50, "y": 50, "z": 0,
                      "observedAt": "2026-09-13T12:06:00Z", "itemCount": 1, "contentFingerprint": "fp-b-3",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 3000, "itemName": "Nowy Przedmiot C", "count": 1, "priceRaw": 6000, "unitPrice": 6000, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        // 3. Newer partial completed run (Run C, publishable=false)
        importPayload(http, """
                {
                  "sourceId": "multi-run-test",
                  "batchId": "batch-run-c",
                  "runs": [
                    {
                      "runId": "run-c-partial-new",
                      "startedAt": "2026-09-13T14:00:00Z",
                      "endedAt": "2026-09-13T14:05:00Z",
                      "state": 3,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 10,
                      "visitedTargets": 10,
                      "failedTargets": 0,
                      "publishable": false
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-c-1", "runId": "run-c-partial-new", "shopVid": 301, "shopTitle": "Shop C1",
                      "ownerName": "OwnerC1", "mapId": "map-1", "channel": 1, "x": 60, "y": 60, "z": 0,
                      "observedAt": "2026-09-13T14:02:00Z", "itemCount": 1, "contentFingerprint": "fp-c-1",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 1000, "itemName": "Stary Przedmiot A", "count": 1, "priceRaw": 100, "unitPrice": 100, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-c-2", "runId": "run-c-partial-new", "shopVid": 302, "shopTitle": "Shop C2",
                      "ownerName": "OwnerC2", "mapId": "map-1", "channel": 1, "x": 70, "y": 70, "z": 0,
                      "observedAt": "2026-09-13T14:03:00Z", "itemCount": 1, "contentFingerprint": "fp-c-2",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 4000, "itemName": "Czesciowy Przedmiot D", "count": 1, "priceRaw": 50, "unitPrice": 50, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        // 4. Newer failed run (Run D, state=4, publishable=true)
        importPayload(http, """
                {
                  "sourceId": "multi-run-test",
                  "batchId": "batch-run-d",
                  "runs": [
                    {
                      "runId": "run-d-failed-new",
                      "startedAt": "2026-09-13T16:00:00Z",
                      "endedAt": "2026-09-13T16:05:00Z",
                      "state": 4,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 50,
                      "visitedTargets": 5,
                      "failedTargets": 45,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-d-1", "runId": "run-d-failed-new", "shopVid": 401, "shopTitle": "Shop D1",
                      "ownerName": "OwnerD1", "mapId": "map-1", "channel": 1, "x": 80, "y": 80, "z": 0,
                      "observedAt": "2026-09-13T16:02:00Z", "itemCount": 1, "contentFingerprint": "fp-d-1",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 1000, "itemName": "Stary Przedmiot A", "count": 1, "priceRaw": 10, "unitPrice": 10, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        JsonNode overview = getJson(http, "/api/v1/items/overview");
        assertThat(overview.path("observedShopCount").asLong()).isEqualTo(3);
        assertThat(overview.path("items")).extracting(item -> item.path("vnum").asInt())
                .containsExactly(3000, 1000);
        assertThat(overview.path("items").get(0).path("shopCount").asLong()).isEqualTo(2);
        assertThat(overview.path("items").get(0).path("minimumPrice").asLong()).isEqualTo(5000);

        // Verify offers in public search reflect strictly Run B (the latest completed full/publishable run)
        JsonNode searchA = getJson(http, "/api/v1/items?query=Stary%20Przedmiot%20A");
        assertThat(searchA.path("totalElements").asLong()).isEqualTo(1);
        assertThat(searchA.path("items").get(0).path("price").asLong()).isEqualTo(800);
        assertThat(searchA.path("items").get(0).path("shop").path("title").asText()).isEqualTo("Shop B1");

        // Verify older full run's exclusive item (vnum 2000) is no longer present
        JsonNode searchOld = getJson(http, "/api/v1/items?vnum=2000");
        assertThat(searchOld.path("totalElements").asLong()).isEqualTo(0);

        // Verify partial run's item (vnum 4000) is NOT present in search
        JsonNode searchPartial = getJson(http, "/api/v1/items?vnum=4000");
        assertThat(searchPartial.path("totalElements").asLong()).isEqualTo(0);

        // Verify statistics for vnum 1000 reflect strictly Run B
        JsonNode stats1000 = getJson(http, "/api/v1/items/statistics?vnum=1000").path("items").get(0);
        assertThat(stats1000.path("minimumPrice").asLong()).isEqualTo(800);
        assertThat(stats1000.path("meanPrice").decimalValue()).isEqualByComparingTo("800");
        assertThat(stats1000.path("medianPrice").decimalValue()).isEqualByComparingTo("800");
        assertThat(stats1000.path("contributingShopCount").asLong()).isEqualTo(1);
        assertThat(stats1000.path("rawOfferCount").asLong()).isEqualTo(1);

        // Verify statistics for vnum 3000 from Run B
        JsonNode stats3000 = getJson(http, "/api/v1/items/statistics?vnum=3000").path("items").get(0);
        assertThat(stats3000.path("minimumPrice").asLong()).isEqualTo(5000);
        assertThat(stats3000.path("meanPrice").decimalValue()).isEqualByComparingTo("5500");
        assertThat(stats3000.path("medianPrice").decimalValue()).isEqualByComparingTo("5500");
        assertThat(stats3000.path("percentiles").path("p50").asLong()).isEqualTo(5000);
        assertThat(stats3000.path("contributingShopCount").asLong()).isEqualTo(2);
        assertThat(stats3000.path("rawOfferCount").asLong()).isEqualTo(2);

        // Verify multi-VNUM search returns offers across vnum 1000 and 3000 within Run B
        JsonNode multiVnumSearch = getJson(http, "/api/v1/items?vnum=1000&vnum=3000&size=20");
        assertThat(multiVnumSearch.path("totalElements").asLong()).isEqualTo(3);
        assertThat(multiVnumSearch.path("items")).extracting(item -> item.path("vnum").asInt())
                .containsExactlyInAnyOrder(1000, 3000, 3000);

        // Verify multi-VNUM statistics
        JsonNode multiVnumStats = getJson(http, "/api/v1/items/statistics?vnum=1000&vnum=3000").path("items");
        assertThat(multiVnumStats).hasSize(2);

        // Verify suggestions include items from all runs across the historical catalog
        JsonNode suggestions = getJson(http, "/api/v1/items/suggestions?query=Czesciowy");
        assertThat(suggestions.path("totalMatches").asLong()).isEqualTo(1);
        assertThat(suggestions.path("suggestions").get(0).path("name").asText()).isEqualTo("Czesciowy Przedmiot D");
    }

    @Test
    void rejectsExceedingLimitsAndReturnsBadRequest() throws Exception {
        HttpClient http = HttpClient.newHttpClient();

        // 101 vnums in search -> 400 Bad Request
        StringBuilder searchVnums = new StringBuilder("/api/v1/items?");
        for (int i = 1; i <= 101; i++) {
            if (i > 1) searchVnums.append("&");
            searchVnums.append("vnum=").append(i);
        }
        HttpResponse<String> searchResp = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + searchVnums)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(searchResp.statusCode()).isEqualTo(400);

        // Page size > 100 -> 400 Bad Request
        HttpResponse<String> sizeResp = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/items?size=101")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sizeResp.statusCode()).isEqualTo(400);

        // 101 vnums in statistics -> 400 Bad Request
        StringBuilder statsVnums = new StringBuilder("/api/v1/items/statistics?");
        for (int i = 1; i <= 101; i++) {
            if (i > 1) statsVnums.append("&");
            statsVnums.append("vnum=").append(i);
        }
        HttpResponse<String> statsResp = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + statsVnums)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(statsResp.statusCode()).isEqualTo(400);
    }

    @Test
    void deduplicatesRepeatShopObservationsInSameScanRun() throws Exception {
        HttpClient http = HttpClient.newHttpClient();

        // One scan run where shop VID 555 is observed twice (older at 10:00, newer at 10:05)
        importPayload(http, """
                {
                  "sourceId": "dedup-test",
                  "batchId": "dedup-batch-1",
                  "runs": [
                    {
                      "runId": "dedup-run-1",
                      "startedAt": "2026-09-14T10:00:00Z",
                      "endedAt": "2026-09-14T10:10:00Z",
                      "state": 3,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 10,
                      "visitedTargets": 2,
                      "failedTargets": 0,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-older", "runId": "dedup-run-1", "shopVid": 555, "shopTitle": "Shop Old",
                      "ownerName": "Owner1", "mapId": "map-1", "channel": 1, "x": 10, "y": 10, "z": 0,
                      "observedAt": "2026-09-14T10:00:00Z", "itemCount": 1, "contentFingerprint": "fp-old",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 500, "itemName": "Item Old", "count": 10, "priceRaw": 1000, "unitPrice": 1000, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-newer", "runId": "dedup-run-1", "shopVid": 555, "shopTitle": "Shop New",
                      "ownerName": "Owner1", "mapId": "map-1", "channel": 1, "x": 10, "y": 10, "z": 0,
                      "observedAt": "2026-09-14T10:05:00Z", "itemCount": 2, "contentFingerprint": "fp-new",
                      "listings": [
                        {"listingId": 2, "slotIndex": 0, "vnum": 500, "itemName": "Item New", "count": 3, "priceRaw": 700, "unitPrice": 700, "tailField": 0, "attributes": [], "sockets": []},
                        {"listingId": 3, "slotIndex": 1, "vnum": 500, "itemName": "Item New", "count": 2, "priceRaw": 800, "unitPrice": 800, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-other-shop", "runId": "dedup-run-1", "shopVid": 666, "shopTitle": "Shop Other",
                      "ownerName": "Owner2", "mapId": "map-1", "channel": 1, "x": 20, "y": 20, "z": 0,
                      "observedAt": "2026-09-14T10:06:00Z", "itemCount": 1, "contentFingerprint": "fp-other",
                      "listings": [
                        {"listingId": 4, "slotIndex": 0, "vnum": 500, "itemName": "Item New", "count": 5, "priceRaw": 900, "unitPrice": 900, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        JsonNode overview = getJson(http, "/api/v1/items/overview");
        assertThat(overview.path("items")).hasSize(1);
        JsonNode overviewItem = overview.path("items").get(0);
        assertThat(overviewItem.path("itemName").asText()).isEqualTo("Item New");
        assertThat(overviewItem.path("shopCount").asLong()).isEqualTo(2);
        assertThat(overviewItem.path("totalQuantity").asLong()).isEqualTo(10);
        assertThat(overviewItem.path("minimumPrice").asLong()).isEqualTo(700);

        // Search returns only from canonical observations: Shop 555 has 2 listings, Shop 666 has 1 listing -> total 3
        JsonNode search = getJson(http, "/api/v1/items?vnum=500&size=20");
        assertThat(search.path("totalElements").asLong()).isEqualTo(3);

        // Statistics reflects exactly 2 unique contributing shops (Shop 555 and Shop 666)
        JsonNode stats = getJson(http, "/api/v1/items/statistics?vnum=500").path("items").get(0);
        assertThat(stats.path("contributingShopCount").asLong()).isEqualTo(2);
        // Shop 555 has 2 listings (700 and 800), Shop 666 has 1 listing (900) -> total 3 listings
        assertThat(stats.path("rawOfferCount").asLong()).isEqualTo(3);
        // Total quantity: Shop 555 has 3+2=5, Shop 666 has 5 -> total 10 (not including old 10 units)
        assertThat(stats.path("totalQuantity").asLong()).isEqualTo(10);
        // Minimum price is 700 (from newer observation)
        assertThat(stats.path("minimumPrice").asLong()).isEqualTo(700);
        // Shop-level distribution: Shop 555 min=700, Shop 666 min=900
        // Mean = (700 + 900) / 2 = 800
        assertThat(stats.path("meanPrice").decimalValue()).isEqualByComparingTo("800");
        // Classical median: (700 + 900) / 2 = 800
        assertThat(stats.path("medianPrice").decimalValue()).isEqualByComparingTo("800");
        // Discrete P50 = 700 (ceil(2 * 0.5) = 1 -> index 0)
        assertThat(stats.path("percentiles").path("p50").asLong()).isEqualTo(700);

        // Buyer reference (P20 = 700)
        assertThat(stats.path("buyerReference").path("price").asLong()).isEqualTo(700);
        assertThat(stats.path("buyerReference").path("shopsAtOrBelow").asLong()).isEqualTo(1);
        // Quantity at or below 700: only the 700 listing (qty=3), not the 800 or 900
        assertThat(stats.path("buyerReference").path("quantityAtOrBelow").asLong()).isEqualTo(3);
    }

    @Test
    void supportsNullShopVidFallbackAndExtendedStatisticsPayload() throws Exception {
        HttpClient http = HttpClient.newHttpClient();

        importPayload(http, """
                {
                  "sourceId": "null-vid-test",
                  "batchId": "null-vid-batch-1",
                  "runs": [
                    {
                      "runId": "null-vid-run-1",
                      "startedAt": "2026-09-15T10:00:00Z",
                      "endedAt": "2026-09-15T10:10:00Z",
                      "state": 3,
                      "mapId": "map-1",
                      "channel": 1,
                      "totalTargets": 10,
                      "visitedTargets": 2,
                      "failedTargets": 0,
                      "publishable": true
                    }
                  ],
                  "observations": [
                    {
                      "observationId": "obs-null-1", "runId": "null-vid-run-1", "shopVid": null, "shopTitle": "Shop Null 1",
                      "ownerName": "Owner1", "mapId": "map-1", "channel": 1, "x": 10, "y": 10, "z": 0,
                      "observedAt": "2026-09-15T10:01:00Z", "itemCount": 1, "contentFingerprint": "fp-null-1",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 888, "itemName": "Bialy Kamien", "count": 2, "priceRaw": 100, "unitPrice": 100, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    },
                    {
                      "observationId": "obs-null-2", "runId": "null-vid-run-1", "shopVid": null, "shopTitle": "Shop Null 2",
                      "ownerName": "Owner2", "mapId": "map-1", "channel": 1, "x": 20, "y": 20, "z": 0,
                      "observedAt": "2026-09-15T10:02:00Z", "itemCount": 1, "contentFingerprint": "fp-null-2",
                      "listings": [
                        {"listingId": 1, "slotIndex": 0, "vnum": 888, "itemName": "Bialy Kamien", "count": 3, "priceRaw": 200, "unitPrice": 200, "tailField": 0, "attributes": [], "sockets": []}
                      ]
                    }
                  ]
                }
                """);

        JsonNode overview = getJson(http, "/api/v1/items/overview");
        assertThat(overview.path("observedShopCount").asLong()).isEqualTo(2);
        assertThat(overview.path("items").get(0).path("shopCount").asLong()).isEqualTo(2);
        assertThat(overview.path("items").get(0).path("totalQuantity").asLong()).isEqualTo(5);

        JsonNode stats = getJson(http, "/api/v1/items/statistics?vnum=888").path("items").get(0);
        assertThat(stats.path("vnum").asInt()).isEqualTo(888);
        assertThat(stats.path("itemName").asText()).isEqualTo("Bialy Kamien");
        assertThat(stats.path("contributingShopCount").asLong()).isEqualTo(2);
        assertThat(stats.path("minimumPrice").asLong()).isEqualTo(100);
        assertThat(stats.path("percentiles").path("p10").asLong()).isEqualTo(100);
        assertThat(stats.path("percentiles").path("p90").asLong()).isEqualTo(200);
        assertThat(stats.path("iqr").asLong()).isEqualTo(100);
        assertThat(stats.path("outliers").path("totalCount").asLong()).isEqualTo(0);
        assertThat(stats.path("depth")).hasSize(2);
        assertThat(stats.path("histogram")).isNotEmpty();
    }

    @Test
    void overviewSupportsEmptyMarketsAndKeepsBeaviumIsolated() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        JsonNode empty = getJson(http, "/api/v1/servers/beavium/items/overview");
        assertThat(empty.path("scanId").isNull()).isTrue();
        assertThat(empty.path("scanEndedAt").isNull()).isTrue();
        assertThat(empty.path("observedShopCount").asLong()).isZero();
        assertThat(empty.path("items")).isEmpty();

        String body = new ClassPathResource("import-example.json").getContentAsString(StandardCharsets.UTF_8);
        assertThat(postImport(http, "/internal/v1/servers/beavium/imports", "beavium-test-token", body)
                .statusCode()).isEqualTo(200);
        JsonNode populated = getJson(http, "/api/v1/servers/beavium/items/overview");
        assertThat(populated.path("items")).hasSize(1);
        assertThat(populated.path("items").get(0).path("vnum").asInt()).isEqualTo(180);
        assertThat(getJson(http, "/api/v1/servers/pandora/items/overview")).isEqualTo(empty);
        assertThat(getJson(http, "/api/v1/servers/elder/items/overview")).isEqualTo(empty);
    }

    @Test
    void sortsAllAggregatedOffersBeforePaginationOnEveryServer() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String body = """
                {
                  "sourceId": "offer-sorting", "batchId": "offer-sorting-batch",
                  "runs": [{"runId": "sorting-run", "startedAt": "2026-10-04T10:00:00Z",
                    "endedAt": "2026-10-04T10:05:00Z", "state": 3, "mapId": "test", "channel": 1,
                    "totalTargets": 1, "visitedTargets": 1, "failedTargets": 0, "publishable": true}],
                  "observations": [{"observationId": "sorting-shop", "runId": "sorting-run", "shopVid": 123,
                    "shopTitle": "Sorting", "ownerName": "Tester", "mapId": "test", "channel": 1,
                    "x": 1, "y": 1, "z": 0, "observedAt": "2026-10-04T10:00:00Z", "itemCount": 5,
                    "contentFingerprint": "sorting-fingerprint", "listings": [
                      {"listingId": 1, "slotIndex": 0, "vnum": 80050, "itemName": "Sortowany medal", "count": 1, "priceRaw": 100, "unitPrice": 100, "tailField": 0, "attributes": [], "sockets": []},
                      {"listingId": 2, "slotIndex": 1, "vnum": 80050, "itemName": "Sortowany medal", "count": 2, "priceRaw": 2000, "unitPrice": 1000, "tailField": 0, "attributes": [], "sockets": []},
                      {"listingId": 3, "slotIndex": 2, "vnum": 80050, "itemName": "Sortowany medal", "count": 1, "priceRaw": 500, "unitPrice": 500, "tailField": 0, "attributes": [], "sockets": []},
                      {"listingId": 4, "slotIndex": 3, "vnum": 80050, "itemName": "Sortowany medal", "count": 1, "priceRaw": 500, "unitPrice": 500, "tailField": 0, "attributes": [], "sockets": []},
                      {"listingId": 5, "slotIndex": 4, "vnum": 80050, "itemName": "Sortowany medal", "count": 10, "priceRaw": 3000, "unitPrice": 300, "tailField": 0, "attributes": [], "sockets": []}
                    ]}]
                }
                """;
        for (String server : new String[]{"pandora", "elder", "beavium"}) {
            String token = server.equals("pandora") ? "test-token" : server + "-test-token";
            assertThat(postImport(http, "/internal/v1/servers/" + server + "/imports", token, body).statusCode()).isEqualTo(200);
            String route = "/api/v1/servers/" + server + "/items?vnum=80050&query=Sortowany&size=2";
            JsonNode cheapest = getJson(http, route);
            assertThat(cheapest.path("totalElements").asLong()).isEqualTo(4);
            assertThat(cheapest.path("items")).extracting(item -> item.path("unitPrice").asLong()).containsExactly(100L, 300L);
            assertThat(getJson(http, route + "&sort=priceAsc")).isEqualTo(cheapest);
            assertThat(getJson(http, route + "&sort=priceDesc").path("items"))
                    .extracting(item -> item.path("unitPrice").asLong()).containsExactly(1000L, 500L);
            assertThat(getJson(http, route + "&sort=priceDesc&page=1").path("items"))
                    .extracting(item -> item.path("unitPrice").asLong()).containsExactly(300L, 100L);
            JsonNode quantityPage = getJson(http, route + "&sort=quantity");
            assertThat(quantityPage.path("items")).extracting(item -> item.path("unitPrice").asLong()).containsExactly(300L, 500L);
            assertThat(quantityPage.path("items")).extracting(item -> item.path("totalQuantity").asLong()).containsExactly(10L, 2L);
            assertThat(quantityPage.path("items").get(1).path("listingCount").asInt()).isEqualTo(2);
            assertThat(getJson(http, route + "&sort=quantity&page=1").path("items"))
                    .extracting(item -> item.path("unitPrice").asLong()).containsExactly(1000L, 100L);
            assertThat(getJson(http, route + "&sort=quantity&page=3").path("totalElements").asLong()).isEqualTo(4);
            assertThat(getJson(http, route + "&sort=quantity&page=3").path("items")).isEmpty();
        }
        assertThat(getJson(http, "/api/v1/items?vnum=80050&size=2&sort=priceDesc").path("items"))
                .extracting(item -> item.path("unitPrice").asLong()).containsExactly(1000L, 500L);
    }

    private JsonNode importPayload(HttpClient http, String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/imports"))
                .header("X-Scanner-Token", "test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> postImport(HttpClient http, String path, String token, String payload)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Scanner-Token", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(HttpClient http, String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
