package com.mazikox.metin_market_api.market.domain;

import java.util.HashSet;
import java.util.List;

/** One extra offer attribute must satisfy each condition; catalog applies are excluded. */
public record BonusFilter(int type, Integer minimum) {
    public static final int MAX_FILTERS = 7;

    public static List<BonusFilter> parse(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > MAX_FILTERS) throw new IllegalArgumentException("At most 7 bonus filters are allowed");
        var types = new HashSet<Integer>();
        return values.stream().map(value -> {
            if (!value.matches("[1-9][0-9]{0,9}(:-?[0-9]{1,10})?"))
                throw new IllegalArgumentException("Expected bonus=type or bonus=type:minimum");
            String[] parts = value.split(":");
            int type = Integer.parseInt(parts[0]);
            if (!types.add(type)) throw new IllegalArgumentException("Repeated bonus type");
            return new BonusFilter(type, parts.length == 2 ? Integer.valueOf(parts[1]) : null);
        }).toList();
    }
}
