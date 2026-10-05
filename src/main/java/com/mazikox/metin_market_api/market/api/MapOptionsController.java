package com.mazikox.metin_market_api.market.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/items/map-options", "/api/v1/servers/{server}/items/map-options"})
public class MapOptionsController {
    private final JdbcClient jdbc;
    public MapOptionsController(JdbcClient jdbc) { this.jdbc = jdbc; }
    public record MapOption(String mapId, long scanId, OffsetDateTime scanEndedAt) {}
    @GetMapping
    public List<MapOption> get() {
        return jdbc.sql("SELECT market_map_id, id, ended_at FROM market_active_scan ORDER BY market_map_id")
            .query((rs, row) -> new MapOption(rs.getString(1), rs.getLong(2), rs.getObject(3, OffsetDateTime.class))).list();
    }
}
