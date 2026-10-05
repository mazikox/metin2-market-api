package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.catalog.domain.ItemDefinition;
import com.mazikox.metin_market_api.market.domain.ItemBonusCatalog;
import com.mazikox.metin_market_api.server.domain.GameServer;
import java.util.List;

public record ItemMetadata(int vnum, String name, int type, int subtype, int size, long antiFlags,
                           int requiredLevel, int defense, int minAttack, int maxAttack,
                           int minMagicAttack, int maxMagicAttack, int socketCount,
                           List<ItemSearchResult.ItemAttribute> builtInBonuses) {
    public static ItemMetadata from(ItemDefinition item, GameServer server) {
        if (item == null) return null;
        var bonuses = item.builtInBonuses().stream().map(b -> {
            var details = ItemBonusCatalog.describe(server, b.type(), b.value());
            return new ItemSearchResult.ItemAttribute(b.slotIndex(), b.type(), details.code(),
                    details.name(), b.value(), details.displayValue());
        }).toList();
        return new ItemMetadata(item.vnum(), item.name(), item.type(), item.subtype(), item.size(),
                item.antiFlags(), item.requiredLevel(), item.defense(), item.minAttack(), item.maxAttack(),
                item.minMagicAttack(), item.maxMagicAttack(), item.socketCount(), bonuses);
    }
}
