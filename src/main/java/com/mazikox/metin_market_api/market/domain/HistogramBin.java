package com.mazikox.metin_market_api.market.domain;

public record HistogramBin(
        long fromPrice,
        long toPrice,
        long shopCount
) {}
