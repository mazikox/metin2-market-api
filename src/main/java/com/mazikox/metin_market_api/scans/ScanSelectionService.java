package com.mazikox.metin_market_api.scans;

import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ScanSelectionService {
    private final JdbcClient jdbc;
    public ScanSelectionService(JdbcClient jdbc) { this.jdbc = jdbc; }
    public record Scan(long id, String sourceId, String sourceRunId, OffsetDateTime startedAt,
                       OffsetDateTime endedAt, int state, boolean publishable, Integer channel,
                       Integer expectedObservations, long observations, long listings, boolean eligible) {}
    public record MapSelection(String mapId, boolean enabled, Long selectedScanId, Long activeScanId,
                               List<Scan> scans) {}
    public record Report(String server, List<MapSelection> maps) {}

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Report report() {
        var maps = jdbc.sql("""
                WITH maps AS (SELECT map_id FROM market_map_selection UNION SELECT market_map_id FROM scan_run)
                SELECT m.map_id, COALESCE(s.enabled, true) AS enabled, s.selected_scan_id, a.id AS active_id
                FROM maps m LEFT JOIN market_map_selection s ON s.map_id = m.map_id
                LEFT JOIN market_active_scan a ON a.market_map_id = m.map_id ORDER BY m.map_id
                """).query((rs, row) -> new MapSelection(rs.getString("map_id"), rs.getBoolean("enabled"),
                    (Long)rs.getObject("selected_scan_id"), (Long)rs.getObject("active_id"), List.of())).list();
        var result = new ArrayList<MapSelection>();
        for (var map : maps) {
            var scans = jdbc.sql("""
                    WITH recent AS (
                        SELECT r.*, row_number() OVER (ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC) AS rn
                        FROM scan_run r WHERE market_map_id = :map
                    )
                    SELECT r.*, e.id IS NOT NULL AS eligible,
                           r.imported_observations AS observations,
                           (SELECT count(*) FROM shop_listing l JOIN shop_observation o ON o.id = l.observation_id
                            WHERE o.scan_run_id = r.id) AS listings
                    FROM recent r LEFT JOIN market_eligible_scan e ON e.id = r.id
                    WHERE rn <= 100 OR r.id = :selected
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, r.id DESC
                    """).param("map", map.mapId()).param("selected", map.selectedScanId())
                    .query((rs, row) -> new Scan(rs.getLong("id"), rs.getString("source_id"), rs.getString("source_run_id"),
                        rs.getObject("started_at", OffsetDateTime.class), rs.getObject("ended_at", OffsetDateTime.class),
                        rs.getInt("state"), rs.getBoolean("publishable"), (Integer)rs.getObject("channel"),
                        (Integer)rs.getObject("expected_observations"), rs.getLong("observations"),
                        rs.getLong("listings"), rs.getBoolean("eligible"))).list();
            result.add(new MapSelection(map.mapId(), map.enabled(), map.selectedScanId(), map.activeScanId(), scans));
        }
        return new Report(ServerContext.requireCurrent().slug(), result);
    }

    @Transactional
    public Report update(List<AdminScansController.Selection> selections) {
        var seen = new HashSet<String>();
        for (var selection : selections) {
            String map = jdbc.sql("SELECT market_map_key(:map)").param("map", selection.mapId()).query(String.class).single();
            if (!seen.add(map)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate map");
            long known = jdbc.sql("""
                    SELECT count(*) FROM (SELECT map_id FROM market_map_selection
                    UNION SELECT market_map_id FROM scan_run) m WHERE map_id = :map
                    """).param("map", map).query(Long.class).single();
            if (known == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown map");
            if (selection.selectedScanId() != null) {
                var valid = jdbc.sql("""
                        SELECT r.id FROM scan_run r JOIN market_eligible_scan e ON e.id = r.id
                        WHERE r.id = :id AND r.market_map_id = :map FOR SHARE OF r
                        """).param("id", selection.selectedScanId()).param("map", map).query(Long.class).optional();
                if (valid.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Scan is not eligible for this map");
            }
            jdbc.sql("""
                    INSERT INTO market_map_selection (map_id, enabled, selected_scan_id)
                    VALUES (:map, :enabled, :selected)
                    ON CONFLICT (map_id) DO UPDATE SET enabled = EXCLUDED.enabled,
                        selected_scan_id = EXCLUDED.selected_scan_id, updated_at = now()
                    """).param("map", map).param("enabled", selection.enabled())
                    .param("selected", selection.selectedScanId()).update();
        }
        return report();
    }
}
