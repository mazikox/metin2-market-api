package com.mazikox.metin_market_api.analytics;

import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import org.springframework.beans.factory.annotation.Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalyticsIntegrationTest {
    static final String SECRET = "analytics-secret-only-for-tests-32-chars";
    static final String PROXY = "proxy-token-only-for-tests-32-chars";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    static { postgres.start(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("app.scanner-token.pandora", () -> "test-token");
        r.add("app.scanner-token.elder", () -> "elder-test-token");
        r.add("app.scanner-token.beavium", () -> "beavium-test-token");
        r.add("app.analytics.enabled", () -> true);
        r.add("app.analytics.secret", () -> SECRET);
        r.add("app.analytics.proxy-token", () -> PROXY);
    }
    @Autowired DataSource source;
    @Value("${local.server.port}") int port;
    AnalyticsService analytics;
    JdbcTemplate jdbc;
    MutableClock clock;
    static class MutableClock extends Clock {
        Instant time = Instant.parse("2026-10-01T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
        public Instant instant() { return time; }
    }
    @BeforeEach void clean() {
        jdbc = new JdbcTemplate(source); clock = new MutableClock();
        analytics = new AnalyticsService(source, true, SECRET, PROXY, clock);
        ServerContext.withServer(GameServer.PANDORA, () -> jdbc.update("TRUNCATE analytics.daily_totals, analytics.daily_visitors, analytics.daily_items, analytics.request_dedup"));
    }
    MockHttpServletRequest request(String ip, boolean search) {
        var r = new MockHttpServletRequest("GET", "/api/v1/servers/pandora/items");
        r.addHeader("User-Agent", "Mozilla/5.0"); r.addHeader("X-Analytics-Proxy-Token", PROXY);
        r.addHeader("X-Analytics-Client-IP", ip); r.addHeader("X-Catalog-Request", UUID.randomUUID().toString());
        if (search) r.addHeader("X-Catalog-Search", UUID.randomUUID().toString());
        return r;
    }
    void record(MockHttpServletRequest r, GameServer server, int page) {
        ServerContext.withServer(server, () -> { analytics.record(r, page, "test", List.of()); return null; });
    }
    long sum(String column, String server) {
        return ServerContext.withServer(GameServer.PANDORA, () -> jdbc.queryForObject("SELECT coalesce(sum(" + column + "),0) FROM analytics.daily_totals WHERE server=?", Long.class, server));
    }
    long count(String table) {
        return ServerContext.withServer(GameServer.PANDORA, () -> jdbc.queryForObject("SELECT count(*) FROM analytics." + table, Long.class));
    }
    @Test void sameIpDeduplicatesDailyAcrossServersButDifferentIpsDoNot() {
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        record(request("198.51.100.1", true), GameServer.ELDER, 0);
        assertThat(sum("uniques", "all")).isEqualTo(1);
        assertThat(sum("uniques", "elder")).isEqualTo(1);
        record(request("198.51.100.2", true), GameServer.BEAVIUM, 0);
        assertThat(sum("uniques", "all")).isEqualTo(2);
        assertThat(sum("searches", "all")).isEqualTo(4);
    }
    @Test void nextWarsawDayChangesHashCountsNewUniqueAndPrunesOldVisitors() {
        var first = request("198.51.100.1", true);
        clock.time = Instant.parse("2026-10-01T21:59:59Z"); record(first, GameServer.PANDORA, 0);
        String oldHash = analytics.visitorHash(LocalDate.parse("2026-10-01"), "198.51.100.1");
        clock.time = Instant.parse("2026-10-01T22:00:00Z"); record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        assertThat(analytics.visitorHash(LocalDate.parse("2026-10-02"), "198.51.100.1")).isNotEqualTo(oldHash);
        assertThat(sum("uniques", "all")).isEqualTo(2);
        assertThat(count("daily_visitors")).isEqualTo(2); // only current-day global + server
        clock.time = Instant.parse("2026-10-04T12:00:00Z"); analytics.cleanup();
        assertThat(count("daily_visitors")).isZero();
        assertThat(count("request_dedup")).isZero();
        assertThat(sum("uniques", "all")).isEqualTo(2);
    }
    @Test void retriesAndPaginationDoNotCreateSearchesAndInitialLoadOnlyCountsResult() {
        record(request("198.51.100.1", false), GameServer.PANDORA, 0);
        var search = request("198.51.100.1", true);
        record(search, GameServer.PANDORA, 0); record(search, GameServer.PANDORA, 0);
        var page = request("198.51.100.1", false);
        page.addHeader("X-Catalog-Search", search.getHeader("X-Catalog-Search"));
        record(page, GameServer.PANDORA, 1);
        var back = request("198.51.100.1", false);
        back.addHeader("X-Catalog-Search", search.getHeader("X-Catalog-Search"));
        record(back, GameServer.PANDORA, 0);
        assertThat(sum("searches", "all")).isEqualTo(1);
        assertThat(sum("results", "all")).isEqualTo(4);
    }
    @Test void searchRetryAcrossMidnightDoesNotBecomeANewSearch() {
        var r = request("198.51.100.1", true);
        record(r, GameServer.PANDORA, 0);
        clock.time = clock.time.plus(Duration.ofDays(1));
        record(r, GameServer.PANDORA, 0);
        assertThat(sum("searches", "all")).isEqualTo(1);
        assertThat(sum("results", "all")).isEqualTo(1);
    }
    @Test void popularItemsAreIndependentAggregatesOfCanonicalCatalogNames() throws Exception {
        String payload = new org.springframework.core.io.ClassPathResource("import-example.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var imported = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/imports"))
                .header("X-Scanner-Token", "test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(imported.statusCode()).isEqualTo(200);
        for (String ip : List.of("198.51.100.1", "198.51.100.2")) {
            ServerContext.withServer(GameServer.PANDORA, () -> {
                analytics.record(request(ip,true), 0, "Zatruty miecz", List.of(180)); return null;
            });
        }
        var popular = (List<?>) analytics.report(14, 20).get("popular");
        assertThat(popular).hasSize(1);
        assertThat(popular.toString()).contains("searches=2", "pandora").doesNotContain("visitor_hash", "198.51.100");
        assertThat(count("daily_items")).isEqualTo(1);
    }
    @Test void concurrentRequestsDoNotDuplicateTheDailyUnique() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i=0; i<8; i++) tasks.add(() -> {
                record(request("198.51.100.1", true), GameServer.PANDORA, 0); return null;
            });
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertThat(sum("uniques", "all")).isEqualTo(1);
        assertThat(sum("searches", "all")).isEqualTo(8);
        assertThat(sum("results", "all")).isEqualTo(8);
    }
    @Test void independentVpsRetentionDeletesExpiredTokensButPreservesAggregates() throws Exception {
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("ops/retention/analytics-prune.sql"));
        ServerContext.withServer(GameServer.PANDORA, () -> {
            jdbc.update("UPDATE analytics.daily_visitors SET day=CURRENT_DATE-3");
            jdbc.update("UPDATE analytics.request_dedup SET created_at=CURRENT_TIMESTAMP-INTERVAL '48 hours'");
            jdbc.execute(sql); return null;
        });
        assertThat(count("daily_visitors")).isZero();
        assertThat(count("request_dedup")).isZero();
        assertThat(sum("uniques", "all")).isEqualTo(1);
    }
    @Test void botsUntrustedRequestsAndTechnicalProbesAreIgnored() {
        for (String agent : List.of("Googlebot", "curl/8", "python-requests", "uptime-monitor", "HeadlessChrome")) {
            var r = request("198.51.100.1", true); r.removeHeader("User-Agent"); r.addHeader("User-Agent", agent); record(r, GameServer.PANDORA, 0);
        }
        var direct = request("198.51.100.1", true); direct.removeHeader("X-Analytics-Proxy-Token");
        direct.addHeader("X-Forwarded-For", "198.51.100.2"); record(direct, GameServer.PANDORA, 0);
        var probe = request("198.51.100.1", true); probe.removeHeader("X-Catalog-Request"); record(probe, GameServer.PANDORA, 0);
        assertThat(count("daily_totals")).isZero();
    }
    @Test void ipCanonicalizationAndForwardedHeaderSpoofing() {
        assertThat(AnalyticsService.canonicalIp("::ffff:198.51.100.1")).isEqualTo(AnalyticsService.canonicalIp("198.51.100.1"));
        assertThat(AnalyticsService.canonicalIp("2001:db8::1")).isEqualTo(AnalyticsService.canonicalIp("2001:0db8:0:0:0:0:0:1"));
        assertThat(AnalyticsService.canonicalIp("example.com")).isNull();
        var r = request("198.51.100.1", true); r.addHeader("X-Forwarded-For", "198.51.100.2"); record(r, GameServer.PANDORA, 0);
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        assertThat(sum("uniques", "all")).isEqualTo(1);
    }
    @Test void schemaAndReportExposeNoRawIpOrVisitorItemRelationship() {
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        ServerContext.withServer(GameServer.PANDORA, () -> {
            var columns = jdbc.queryForList("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='analytics'");
            assertThat(columns.toString()).doesNotContain("ip_address", "user_agent", "query", "visitor_id");
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='analytics' AND table_name='daily_items'", String.class))
                    .containsExactlyInAnyOrder("day", "server", "item_name", "searches");
            for (String table : List.of("daily_visitors", "request_dedup", "daily_totals", "daily_items"))
                assertThat(jdbc.queryForList("SELECT * FROM analytics." + table).toString()).doesNotContain("198.51.100.1");
            return null;
        });
        assertThat(analytics.report(14,20).toString()).doesNotContain("visitor_hash", "198.51.100.1", "request_id");
    }
    @Test void adminRequiresBothProxyAuthenticationAndCaddyAdminIdentity() throws Exception {
        record(request("198.51.100.1", true), GameServer.PANDORA, 0);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminStatsController(analytics)).build();
        mvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/stats").header("X-Analytics-Admin", "admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/stats").header("X-Analytics-Proxy-Token", PROXY)).andExpect(status().isUnauthorized());
        var response = mvc.perform(get("/api/v1/admin/stats").header("X-Analytics-Admin", "admin").header("X-Analytics-Proxy-Token", PROXY))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain("visitor_hash");
        assertThat(response.getContentAsString()).contains("\"today\":\"2026-10-01\"", "\"day\":\"2026-10-01\"");
    }
    @Test void healthSuggestionsAndPriceStatisticsNeverCountEvenWithEventHeaders() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (String path : List.of("/health", "/api/v1/items/suggestions?query=test", "/api/v1/items/statistics?vnum=1")) {
            var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("User-Agent", "Mozilla/5.0").header("X-Analytics-Proxy-Token", PROXY)
                    .header("X-Analytics-Client-IP", "198.51.100.1")
                    .header("X-Catalog-Search", UUID.randomUUID().toString()).header("X-Catalog-Request", UUID.randomUUID().toString()).build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        }
        assertThat(count("daily_totals")).isZero();
    }
}
