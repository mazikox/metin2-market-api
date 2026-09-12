package com.mazikox.metin_market_api.market;

import java.util.List;

public record ItemSuggestion(
        Kind kind, String name, Integer vnum, List<Integer> memberVnums, List<String> memberNames) {
    public enum Kind { ITEM, UPGRADE_FAMILY }

    public static ItemSuggestion item(String name, int vnum) {
        return new ItemSuggestion(Kind.ITEM, name, vnum, List.of(), List.of());
    }

    public static ItemSuggestion upgradeFamily(String name, List<Integer> memberVnums, List<String> memberNames) {
        return new ItemSuggestion(Kind.UPGRADE_FAMILY, name, null, memberVnums, memberNames);
    }
}
