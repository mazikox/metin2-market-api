package com.mazikox.metin_market_api.market.domain;

public record DepthPoint(
        long price,
        long quantityAtPrice,
        long cumulativeQuantity,
        long shopCountAtPrice,
        long cumulativeShopCount
) {}
