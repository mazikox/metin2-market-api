package com.mazikox.metin_market_api.market.domain;

public record BuyerReference(
        int percentile,
        long price,
        long shopsAtOrBelow,
        long quantityAtOrBelow
) {}
