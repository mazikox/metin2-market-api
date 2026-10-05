package com.mazikox.metin_market_api.market.domain;

import java.util.Arrays;
import java.util.List;

/** Engine item_proto types, matching ElderSuite's equipment classification. */
public enum ItemCategory {
    WEAPONS("weapons", "Broń", 1, List.of(0, 1, 2, 3, 4, 5, 7, 8, 10)),
    SWORDS("swords", "Miecze", 1, List.of(0)),
    DAGGERS("daggers", "Sztylety", 1, List.of(1)),
    BOWS("bows", "Łuki", 1, List.of(2)),
    TWO_HANDED("two-handed", "Broń dwuręczna", 1, List.of(3)),
    BELLS("bells", "Dzwony", 1, List.of(4)),
    FANS("fans", "Wachlarze", 1, List.of(5)),
    ARMORS("armors", "Zbroje", 2, List.of(0)),
    HELMETS("helmets", "Hełmy", 2, List.of(1)),
    SHIELDS("shields", "Tarcze", 2, List.of(2)),
    BRACELETS("bracelets", "Bransolety", 2, List.of(3)),
    FOOTWEAR("footwear", "Buty", 2, List.of(4)),
    NECKLACES("necklaces", "Naszyjniki", 2, List.of(5)),
    EARRINGS("earrings", "Kolczyki", 2, List.of(6)),
    TALISMANS("talismans", "Talizmany", 2, List.of(7)),
    GLOVES("gloves", "Rękawice", 2, List.of(8)),
    BELTS("belts", "Pasy", 34, List.of());

    private final String slug, label;
    private final int type;
    private final List<Integer> subtypes;
    ItemCategory(String slug, String label, int type, List<Integer> subtypes) {
        this.slug = slug; this.label = label; this.type = type; this.subtypes = subtypes;
    }
    public String slug() { return slug; }
    public String label() { return label; }
    public int type() { return type; }
    public List<Integer> subtypes() { return subtypes; }
    public boolean matches(int type, int subtype) {
        return this.type == type && (subtypes.isEmpty() || subtypes.contains(subtype));
    }
    public static ItemCategory parse(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return Arrays.stream(values()).filter(c -> c.slug.equals(slug)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown item category"));
    }
}
