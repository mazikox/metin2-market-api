package com.mazikox.metin_market_api.market.domain;

import java.math.BigDecimal;
import java.util.List;

public record ItemPriceStatistics(
        int vnum,
        String itemName,
        long minimumPrice,
        BigDecimal meanPrice,
        BigDecimal trimmedMeanPrice,
        BigDecimal medianPrice,
        PricePercentiles percentiles,
        long iqr,
        BigDecimal relativeIqr,
        long contributingShopCount,
        long rawOfferCount,
        long totalQuantity,
        long totalPriceLevelCount,
        OutlierSummary outliers,
        BuyerReference buyerReference,
        List<HistogramBin> histogram,
        List<DepthPoint> depth
) {
    public ItemPriceStatistics {
        histogram = histogram != null ? List.copyOf(histogram) : List.of();
        depth = depth != null ? List.copyOf(depth) : List.of();
    }
}
