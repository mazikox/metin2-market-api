package com.mazikox.metin_market_api.catalog.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class ItemProtoParser {
    private static final String HEADER = "vnum\tname\ttype\tsubtype\tsize\tanti\treq_level\tdef\tmin_atk\tmax_atk\tmin_matk\tmax_matk\tsockets\tapp1_t\tapp1_v\tapp2_t\tapp2_v\tapp3_t\tapp3_v";
    private ItemProtoParser() {}

    public static List<ItemDefinition> parse(String text) {
        if (text.length() > 2_000_000) throw new IllegalArgumentException("Catalog exceeds 2 million characters");
        String[] lines = text.replaceFirst("^\uFEFF", "").split("\r?\n", -1);
        if (!HEADER.equals(lines[0])) throw new IllegalArgumentException("Expected the 19-column item_proto TSV header");
        var items = new ArrayList<ItemDefinition>();
        var seen = new HashSet<Integer>();
        for (int line = 1; line < lines.length; line++) {
            if (lines[line].isBlank()) continue;
            try {
                String[] c = lines[line].split("\t", -1);
                if (c.length != 19) throw new IllegalArgumentException("Expected 19 columns");
                int vnum = Integer.parseInt(c[0]);
                if (vnum <= 0 || !seen.add(vnum)) throw new IllegalArgumentException("Invalid or repeated VNUM");
                if (c[1].isBlank() || c[1].contains("\uFFFD")) throw new IllegalArgumentException("Invalid name");
                long anti = Long.parseLong(c[5]);
                if (anti < 0 || anti > 0xFFFFFFFFL) throw new IllegalArgumentException("Invalid anti flags");
                int[] n = new int[19];
                for (int i = 2; i < 19; i++) {
                    if (i == 5) continue;
                    n[i] = Integer.parseInt(c[i]);
                    if (i != 14 && i != 16 && i != 18 && n[i] < 0)
                        throw new IllegalArgumentException("Negative stat or bonus type");
                }
                if (n[4] < 1 || n[4] > 3) throw new IllegalArgumentException("Invalid inventory size");
                var bonuses = new ArrayList<ItemDefinition.BaseBonus>();
                for (int i = 13; i < 19; i += 2) {
                    if (n[i] > 0) bonuses.add(new ItemDefinition.BaseBonus((i - 13) / 2, n[i], n[i+1]));
                    else if (n[i+1] != 0) throw new IllegalArgumentException("Bonus value without type");
                }
                items.add(new ItemDefinition(vnum, c[1], n[2], n[3], n[4], anti, n[6], n[7],
                        n[8], n[9], n[10], n[11], n[12], List.copyOf(bonuses)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("TSV line " + (line + 1) + ": " + e.getMessage());
            }
        }
        if (items.isEmpty()) throw new IllegalArgumentException("Empty catalog");
        return List.copyOf(items);
    }
}
