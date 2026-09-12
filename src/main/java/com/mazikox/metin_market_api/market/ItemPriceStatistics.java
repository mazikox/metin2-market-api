package com.mazikox.metin_market_api.market;

import java.math.BigDecimal;

public record ItemPriceStatistics(
        int vnum, String itemName, long minimumPrice, BigDecimal meanPrice, BigDecimal medianPrice,
        long contributingShopCount, long rawOfferCount, long totalQuantity) {
}
