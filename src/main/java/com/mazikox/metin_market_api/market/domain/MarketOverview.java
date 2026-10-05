package com.mazikox.metin_market_api.market.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record MarketOverview(
        Long scanId, OffsetDateTime scanEndedAt, long observedShopCount, List<PopularItem> items, List<MapScan> maps) {
    public record MapScan(String mapId, long scanId, OffsetDateTime scanEndedAt, long observedShopCount) {}
    public MarketOverview(Long scanId, OffsetDateTime scanEndedAt, long observedShopCount, List<PopularItem> items) {
        this(scanId, scanEndedAt, observedShopCount, items, List.of());
    }
    public record PopularItem(
            int vnum, String itemName, long shopCount, long totalQuantity, long minimumPrice) {}

    public static MarketOverview empty() {
        return new MarketOverview(null, null, 0, List.of());
    }
}
