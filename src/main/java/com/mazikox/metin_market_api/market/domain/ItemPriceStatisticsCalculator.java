package com.mazikox.metin_market_api.market.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ItemPriceStatisticsCalculator {

    public record RawListing(
            int vnum,
            String itemName,
            long unitPrice,
            int quantity,
            String shopKey
    ) {}

    private record ShopAggregated(
            String shopKey,
            long cheapestUnitPrice,
            long offerCount,
            long totalQuantity
    ) {}

    private record DepthAggregationResult(
            long totalPriceLevelCount,
            List<DepthPoint> depthPoints
    ) {}

    private ItemPriceStatisticsCalculator() {}

    public static ItemPriceStatistics calculate(int vnum, List<RawListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return null;
        }

        String itemName = listings.stream()
                .map(RawListing::itemName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("VNUM " + vnum);

        // 1. Group listings by unique shop
        Map<String, List<RawListing>> byShop = new LinkedHashMap<>();
        for (RawListing listing : listings) {
            String key = listing.shopKey() != null ? listing.shopKey() : "unknown";
            byShop.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listing);
        }

        List<ShopAggregated> shops = new ArrayList<>(byShop.size());
        for (Map.Entry<String, List<RawListing>> entry : byShop.entrySet()) {
            List<RawListing> shopListings = entry.getValue();
            long cheapest = shopListings.stream().mapToLong(RawListing::unitPrice).min().orElse(0L);
            long shopOfferCount = shopListings.size();
            long shopQty = shopListings.stream().mapToLong(RawListing::quantity).sum();
            shops.add(new ShopAggregated(entry.getKey(), cheapest, shopOfferCount, shopQty));
        }

        // Sort shops by cheapest unit price ascending
        shops.sort(Comparator.comparingLong(ShopAggregated::cheapestUnitPrice));
        int n = shops.size();
        List<Long> shopPrices = shops.stream().map(ShopAggregated::cheapestUnitPrice).toList();

        long contributingShopCount = n;
        long rawOfferCount = listings.size();
        long totalQuantity = listings.stream().mapToLong(RawListing::quantity).sum();
        long minimumPrice = shopPrices.getFirst();

        // 2. Discrete Percentiles
        long p10 = discretePercentile(shopPrices, 0.10);
        long p20 = discretePercentile(shopPrices, 0.20);
        long p25 = discretePercentile(shopPrices, 0.25);
        long p50 = discretePercentile(shopPrices, 0.50);
        long p75 = discretePercentile(shopPrices, 0.75);
        long p90 = discretePercentile(shopPrices, 0.90);
        PricePercentiles percentiles = new PricePercentiles(p10, p20, p25, p50, p75, p90);

        // 3. Means & Median (classical median for medianPrice, discrete for percentiles.p50)
        BigDecimal meanPrice = calculateMean(shopPrices);
        BigDecimal trimmedMeanPrice = calculateTrimmedMean(shopPrices, 0.10);
        BigDecimal medianPrice = calculateClassicalMedian(shopPrices);

        // 4. IQR & Outliers
        long iqr = p75 - p25;
        BigDecimal relativeIqr = p50 > 0
                ? BigDecimal.valueOf(iqr).divide(BigDecimal.valueOf(p50), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);

        double lowerFence = p25 - 1.5 * iqr;
        double upperFence = p75 + 1.5 * iqr;

        long lowerOutliers = 0;
        long upperOutliers = 0;
        for (long price : shopPrices) {
            if (price < lowerFence) {
                lowerOutliers++;
            } else if (price > upperFence) {
                upperOutliers++;
            }
        }
        OutlierSummary outliers = new OutlierSummary(
                lowerOutliers,
                upperOutliers,
                lowerOutliers + upperOutliers
        );

        // 5. Buyer Reference (P20)
        long buyerPrice = p20;
        long shopsAtOrBelow = shopPrices.stream().filter(p -> p <= buyerPrice).count();
        long quantityAtOrBelow = listings.stream()
                .filter(l -> l.unitPrice() <= buyerPrice)
                .mapToLong(RawListing::quantity)
                .sum();
        BuyerReference buyerReference = new BuyerReference(20, buyerPrice, shopsAtOrBelow, quantityAtOrBelow);

        // 6. Market Depth
        DepthAggregationResult depthResult = calculateMarketDepth(listings, shopPrices);
        long totalPriceLevelCount = depthResult.totalPriceLevelCount();
        List<DepthPoint> depth = depthResult.depthPoints();

        // 7. Histogram
        List<HistogramBin> histogram = calculateHistogram(shopPrices, lowerFence, upperFence);

        return new ItemPriceStatistics(
                vnum,
                itemName,
                minimumPrice,
                meanPrice,
                trimmedMeanPrice,
                medianPrice,
                percentiles,
                iqr,
                relativeIqr,
                contributingShopCount,
                rawOfferCount,
                totalQuantity,
                totalPriceLevelCount,
                outliers,
                buyerReference,
                histogram,
                depth
        );
    }

    public static long discretePercentile(List<Long> sortedPrices, double percentile) {
        if (sortedPrices.isEmpty()) {
            return 0L;
        }
        int n = sortedPrices.size();
        int rank = (int) Math.ceil(n * percentile);
        int index = Math.max(0, Math.min(n - 1, rank - 1));
        return sortedPrices.get(index);
    }

    private static BigDecimal calculateClassicalMedian(List<Long> sortedPrices) {
        if (sortedPrices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int n = sortedPrices.size();
        if (n % 2 == 1) {
            return BigDecimal.valueOf(sortedPrices.get(n / 2));
        }
        long mid1 = sortedPrices.get(n / 2 - 1);
        long mid2 = sortedPrices.get(n / 2);
        return BigDecimal.valueOf(mid1).add(BigDecimal.valueOf(mid2)).divide(BigDecimal.valueOf(2), MathContext.DECIMAL128);
    }

    private static BigDecimal calculateMean(List<Long> prices) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigInteger sum = BigInteger.ZERO;
        for (long price : prices) {
            sum = sum.add(BigInteger.valueOf(price));
        }
        return new BigDecimal(sum).divide(BigDecimal.valueOf(prices.size()), MathContext.DECIMAL128);
    }

    private static BigDecimal calculateTrimmedMean(List<Long> sortedPrices, double trimFraction) {
        int n = sortedPrices.size();
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        int k = (int) Math.floor(n * trimFraction);
        if (n - 2 * k < 1) {
            k = 0;
        }
        BigInteger sum = BigInteger.ZERO;
        int count = 0;
        for (int i = k; i < n - k; i++) {
            sum = sum.add(BigInteger.valueOf(sortedPrices.get(i)));
            count++;
        }
        return new BigDecimal(sum).divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
    }

    private static DepthAggregationResult calculateMarketDepth(List<RawListing> listings, List<Long> sortedShopCheapestPrices) {
        // Group all listings by unit_price
        Map<Long, List<RawListing>> byPrice = new LinkedHashMap<>();
        List<RawListing> sortedListings = new ArrayList<>(listings);
        sortedListings.sort(Comparator.comparingLong(RawListing::unitPrice));

        for (RawListing listing : sortedListings) {
            byPrice.computeIfAbsent(listing.unitPrice(), ignored -> new ArrayList<>()).add(listing);
        }

        long totalPriceLevelCount = byPrice.size();
        List<Map.Entry<Long, List<RawListing>>> priceEntries = new ArrayList<>(byPrice.entrySet());

        if (priceEntries.isEmpty()) {
            return new DepthAggregationResult(0, List.of());
        }

        if (priceEntries.size() <= 100) {
            List<DepthPoint> depthPoints = new ArrayList<>(priceEntries.size());
            long cumulativeQty = 0;
            for (Map.Entry<Long, List<RawListing>> entry : priceEntries) {
                long price = entry.getKey();
                List<RawListing> atPrice = entry.getValue();
                long qtyAtPrice = atPrice.stream().mapToLong(RawListing::quantity).sum();
                long shopCountAtPrice = atPrice.stream().map(RawListing::shopKey).distinct().count();
                cumulativeQty += qtyAtPrice;
                long cumulativeShopCount = sortedShopCheapestPrices.stream().filter(p -> p <= price).count();
                depthPoints.add(new DepthPoint(
                        price,
                        qtyAtPrice,
                        cumulativeQty,
                        shopCountAtPrice,
                        cumulativeShopCount
                ));
            }
            return new DepthAggregationResult(totalPriceLevelCount, depthPoints);
        }

        // More than 100 unique price levels -> bucket deterministically into 100 points
        int targetBuckets = 100;
        int m = priceEntries.size();
        List<DepthPoint> depthPoints = new ArrayList<>(targetBuckets);
        long cumulativeQty = 0;

        for (int b = 0; b < targetBuckets; b++) {
            int startIdx = (b * m) / targetBuckets;
            int endIdx = ((b + 1) * m) / targetBuckets;

            List<Map.Entry<Long, List<RawListing>>> bucketEntries = priceEntries.subList(startIdx, endIdx);
            long bucketMaxPrice = bucketEntries.getLast().getKey();

            long bucketQty = 0;
            Set<String> bucketShops = new TreeSet<>();
            for (Map.Entry<Long, List<RawListing>> entry : bucketEntries) {
                for (RawListing raw : entry.getValue()) {
                    bucketQty += raw.quantity();
                    if (raw.shopKey() != null) {
                        bucketShops.add(raw.shopKey());
                    }
                }
            }

            cumulativeQty += bucketQty;
            long cumulativeShopCount = sortedShopCheapestPrices.stream().filter(p -> p <= bucketMaxPrice).count();

            depthPoints.add(new DepthPoint(
                    bucketMaxPrice,
                    bucketQty,
                    cumulativeQty,
                    bucketShops.size(),
                    cumulativeShopCount
            ));
        }

        return new DepthAggregationResult(totalPriceLevelCount, depthPoints);
    }

    private static List<HistogramBin> calculateHistogram(
            List<Long> sortedShopPrices,
            double lowerFence,
            double upperFence
    ) {
        if (sortedShopPrices.isEmpty()) {
            return List.of();
        }

        // Filter out extreme outliers for the main histogram range
        List<Long> nonOutliers = sortedShopPrices.stream()
                .filter(p -> p >= lowerFence && p <= upperFence)
                .toList();

        if (nonOutliers.isEmpty()) {
            nonOutliers = sortedShopPrices;
        }

        long minP = nonOutliers.getFirst();
        long maxP = nonOutliers.getLast();

        if (minP == maxP || nonOutliers.size() == 1) {
            return List.of(new HistogramBin(minP, maxP, nonOutliers.size()));
        }

        long distinctCount = nonOutliers.stream().distinct().count();
        int binCount = (int) Math.max(3, Math.min(8, distinctCount));

        double range = maxP - minP;
        double step = range / binCount;

        List<HistogramBin> bins = new ArrayList<>(binCount);
        for (int b = 0; b < binCount; b++) {
            long fromPrice = Math.round(minP + b * step);
            long toPrice = (b == binCount - 1) ? maxP : Math.round(minP + (b + 1) * step);
            if (toPrice < fromPrice) {
                toPrice = fromPrice;
            }

            final int binIndex = b;
            final long fPrice = fromPrice;
            final long tPrice = toPrice;

            long count = nonOutliers.stream().filter(p -> {
                if (binIndex == binCount - 1) {
                    return p >= fPrice && p <= tPrice;
                } else {
                    return p >= fPrice && p < tPrice;
                }
            }).count();

            bins.add(new HistogramBin(fromPrice, toPrice, count));
        }

        return bins;
    }
}
