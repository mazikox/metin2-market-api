package com.mazikox.metin_market_api.market;

import java.time.OffsetDateTime;
import java.util.List;

public record ItemSearchResult(
        long listingId, int vnum, String itemName, int quantity, long price, long unitPrice,
        List<ItemAttribute> attributes, List<ItemSocket> sockets, Shop shop, OffsetDateTime observedAt) {
    public record ItemAttribute(int slotIndex, int type, int value) {}
    public record ItemSocket(int socketIndex, long value) {}
    public record Shop(Long vid, String title, String ownerName, String mapId, Integer channel,
                       double x, double y, double z) {}
}
