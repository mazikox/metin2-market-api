package com.mazikox.metin_market_api.market.domain;

public record PricePercentiles(
        long p10,
        long p20,
        long p25,
        long p50,
        long p75,
        long p90
) {}
