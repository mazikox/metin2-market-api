package com.mazikox.metin_market_api.analytics;

import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AnalyticsService {
    private static final Pattern BOT = Pattern.compile("bot|spider|crawl|headless|curl|wget|python|monitor|uptime|httpclient|postman|scanner", Pattern.CASE_INSENSITIVE);
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final String secret;
    private final String proxyToken;
    private final Clock clock;

    @Autowired
    public AnalyticsService(DataSource source,
            @Value("${app.analytics.enabled:false}") boolean enabled,
            @Value("${app.analytics.secret:}") String secret,
            @Value("${app.analytics.proxy-token:}") String proxyToken) {
        this(source, enabled, secret, proxyToken, Clock.systemUTC());
    }

    AnalyticsService(DataSource source, boolean enabled, String secret, String proxyToken, Clock clock) {
        if (enabled && (secret.length() < 32 || proxyToken.length() < 32)) {
            throw new IllegalArgumentException("Enabled analytics requires secrets of at least 32 characters");
        }
        this.jdbc = new JdbcTemplate(source);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        this.enabled = enabled; this.secret = secret; this.proxyToken = proxyToken; this.clock = clock;
    }

    public boolean trusted(HttpServletRequest request) {
        String supplied = request.getHeader("X-Analytics-Proxy-Token");
        return proxyToken.length() >= 32 && supplied != null && MessageDigest.isEqual(
                proxyToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    static String canonicalIp(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")
                || (!value.contains(":") && !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }

    String visitorHash(LocalDate day, String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((day + "\n" + ip).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot create daily pseudonym", e); }
    }

    private static UUID token(String value) {
        if (value == null || !value.matches("[0-9a-fA-F-]{36}")) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public void record(HttpServletRequest request, int page, String query, List<Integer> vnums) {
        if (!enabled || !trusted(request) || !"GET".equals(request.getMethod())) return;
        String agent = request.getHeader("User-Agent");
        if (agent == null || agent.isBlank() || BOT.matcher(agent).find()
                || "prefetch".equalsIgnoreCase(request.getHeader("Purpose"))
                || "prefetch".equalsIgnoreCase(request.getHeader("Sec-Purpose"))) return;
        String ip = canonicalIp(request.getHeader("X-Analytics-Client-IP"));
        UUID resultId = token(request.getHeader("X-Catalog-Request"));
        if (ip == null || resultId == null) return;
        UUID searchId = page == 0 && (!query.isBlank() || !vnums.isEmpty())
                ? token(request.getHeader("X-Catalog-Search")) : null;
        GameServer server = ServerContext.requireCurrent();
        LocalDate day = LocalDate.now(clock.withZone(ZONE));
        String hash = visitorHash(day, ip);
        // The only item labels persisted are names already present in the catalog.
        // Never retain raw user-entered query text (including zero-result searches).
        try {
            List<String> names = searchId == null ? List.of() : itemNames(server, query, vnums);
            ServerContext.withServer(GameServer.PANDORA, () -> transactions.execute(status -> {
                prune(day);
                boolean result = claim("result", resultId);
                boolean search = searchId != null && claim("search", searchId);
                if (!result && !search) return null;
                for (String scope : List.of("all", server.slug())) {
                    int unique = jdbc.update("INSERT INTO analytics.daily_visitors(day,server,visitor_hash) VALUES (?,?,?) ON CONFLICT DO NOTHING", day, scope, hash);
                    jdbc.update("""
                            INSERT INTO analytics.daily_totals(day,server,uniques,searches,results) VALUES (?,?,?,?,?)
                            ON CONFLICT (day,server) DO UPDATE SET
                            uniques=daily_totals.uniques+EXCLUDED.uniques,
                            searches=daily_totals.searches+EXCLUDED.searches,
                            results=daily_totals.results+EXCLUDED.results
                            """, day, scope, unique, search ? 1 : 0, result ? 1 : 0);
                }
                if (search) for (String name : names) jdbc.update("""
                        INSERT INTO analytics.daily_items(day,server,item_name,searches) VALUES (?,?,?,1)
                        ON CONFLICT (day,server,item_name) DO UPDATE SET searches=daily_items.searches+1
                        """, day, server.slug(), name);
                return null;
            }));
        } catch (org.springframework.dao.DataAccessException e) {
            // Analytics failure must not break the catalog or log identifying parameters.
            LoggerFactory.getLogger(getClass()).warn("Catalog analytics write failed ({})", e.getClass().getSimpleName());
        }
    }

    private List<String> itemNames(GameServer server, String query, List<Integer> vnums) {
        String base = "SELECT DISTINCT regexp_replace(item_name, '[+]([0-9]+)$', '') FROM " + server.schema() + ".shop_listing WHERE ";
        if (!vnums.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(vnums.size(), "?"));
            return jdbc.queryForList(base + "item_vnum IN (" + placeholders + ") LIMIT 100", String.class, vnums.toArray());
        }
        return jdbc.queryForList(base + "(lower(regexp_replace(item_name, '[+]([0-9]+)$', '')) = lower(?) OR lower(item_name) = lower(?)) LIMIT 100", String.class, query.strip(), query.strip());
    }

    private boolean claim(String kind, UUID token) {
        return jdbc.update("INSERT INTO analytics.request_dedup(created_at,kind,request_id) VALUES (?,?,?) ON CONFLICT DO NOTHING", java.sql.Timestamp.from(clock.instant()), kind, token) == 1;
    }
    private void prune(LocalDate today) {
        jdbc.update("DELETE FROM analytics.daily_visitors WHERE day < ?", today);
        // Hourly cleanup at 47 hours keeps operation tokens for at most 48 hours.
        jdbc.update("DELETE FROM analytics.request_dedup WHERE created_at <= ?", java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(47))));
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Warsaw")
    public void cleanup() {
        ServerContext.withServer(GameServer.PANDORA, () -> {
            prune(LocalDate.now(clock.withZone(ZONE))); return null;
        });
    }

    public Map<String, Object> report(int days, int limit) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        return ServerContext.withServer(GameServer.PANDORA, () -> Map.of(
            "today", today.toString(), "timezone", ZONE.toString(), "enabled", enabled,
            "daily", jdbc.queryForList("""
                SELECT day::text AS day,server,uniques,searches,results,
                    round(searches::numeric / nullif(uniques,0),2) AS searches_per_user
                FROM analytics.daily_totals WHERE day BETWEEN ? AND ? ORDER BY day DESC,server
                """, today.minusDays(days - 1), today),
            "popular", jdbc.queryForList("""
                SELECT server,item_name,sum(searches) AS searches FROM analytics.daily_items
                WHERE day BETWEEN ? AND ? GROUP BY server,item_name
                ORDER BY searches DESC,server,item_name LIMIT ?
                """, today.minusDays(days - 1), today, limit),
            "itemsByDay", jdbc.queryForList("""
                SELECT day::text AS day,server,item_name,searches FROM (
                    SELECT *,row_number() OVER (PARTITION BY day,server ORDER BY searches DESC,item_name) AS rank
                    FROM analytics.daily_items WHERE day BETWEEN ? AND ?
                ) ranked WHERE rank <= ? ORDER BY day DESC,server,searches DESC,item_name
                """, today.minusDays(days - 1), today, limit)
        ));
    }
}
