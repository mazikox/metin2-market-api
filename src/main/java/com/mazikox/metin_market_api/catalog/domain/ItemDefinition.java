package com.mazikox.metin_market_api.catalog.domain;

import java.util.List;

/** Base catalog data, separate from bonuses and occupied sockets of a scanned offer. */
public record ItemDefinition(int vnum, String name, int type, int subtype, int size, long antiFlags,
                             int requiredLevel, int defense, int minAttack, int maxAttack,
                             int minMagicAttack, int maxMagicAttack, int socketCount,
                             List<BaseBonus> builtInBonuses) {
    public record BaseBonus(int slotIndex, int type, int value) {}
}
