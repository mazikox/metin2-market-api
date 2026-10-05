package com.mazikox.metin_market_api.market.api;

import java.time.LocalDate;
import java.util.List;

public record ItemSearchResult(
        long listingId, int vnum, String itemName, int quantity, long price, long unitPrice,
        long totalQuantity, long totalPrice, int listingCount,
        List<ItemAttribute> attributes, List<ItemSocket> sockets, Shop shop, LocalDate observedAt, ItemMetadata metadata) {
    public record ItemAttribute(int slotIndex, int type, String code, String name, int value,
                                String displayValue) {}
    public record ItemSocket(int socketIndex, long value) {}
    public record Shop(Long vid, String title, String ownerName, String mapId, Integer channel,
                       double x, double y, double z) {}
}
