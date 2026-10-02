package com.mazikox.metin_market_api.market.domain;

public record OutlierSummary(
        long lowerCount,
        long upperCount,
        long totalCount
) {}
