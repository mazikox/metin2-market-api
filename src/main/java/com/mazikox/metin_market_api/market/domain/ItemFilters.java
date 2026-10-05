package com.mazikox.metin_market_api.market.domain;

public record ItemFilters(ItemCategory category, Integer minLevel, Integer maxLevel) {
    public ItemFilters {
        if ((minLevel != null && minLevel < 0) || (maxLevel != null && maxLevel < 0)
                || (minLevel != null && maxLevel != null && minLevel > maxLevel))
            throw new IllegalArgumentException("Invalid required level range");
    }
    public boolean active() { return category != null || minLevel != null || maxLevel != null; }
    public static ItemFilters empty() { return new ItemFilters(null, null, null); }
}
