package com.mazikox.metin_market_api.market.domain;

public record ItemFilters(ItemCategory category, Integer minLevel, Integer maxLevel, java.util.List<String> maps) {
    public ItemFilters(ItemCategory category, Integer minLevel, Integer maxLevel) {
        this(category, minLevel, maxLevel, java.util.List.of());
    }
    public static java.util.List<String> normalizeMaps(java.util.List<String> maps) {
        if (maps == null) return java.util.List.of();
        if (maps.size() > 10 || maps.stream().anyMatch(m -> m == null || !m.matches("[A-Za-z0-9_-]{1,128}")))
            throw new IllegalArgumentException("Invalid map IDs");
        return maps.stream().map(m -> m.equals("metin2_map_a1_summer") ? "metin2_map_a1" : m).distinct().toList();
    }
    public ItemFilters {
        maps = normalizeMaps(maps);
        if ((minLevel != null && minLevel < 0) || (maxLevel != null && maxLevel < 0)
                || (minLevel != null && maxLevel != null && minLevel > maxLevel))
            throw new IllegalArgumentException("Invalid required level range");
    }
    public boolean active() { return category != null || minLevel != null || maxLevel != null; }
    public static ItemFilters empty() { return new ItemFilters(null, null, null); }
}
