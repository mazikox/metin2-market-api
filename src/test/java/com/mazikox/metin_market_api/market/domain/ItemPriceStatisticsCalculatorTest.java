package com.mazikox.metin_market_api.market.domain;

import com.mazikox.metin_market_api.market.domain.ItemPriceStatisticsCalculator.RawListing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPriceStatisticsCalculatorTest {

    @Test
    @DisplayName("Single shop with single listing calculates exact stats")
    void singleShopSingleListing() {
        List<RawListing> listings = List.of(
                new RawListing(100, "Miecz +0", 1000L, 5, "shop-1")
        );

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(100, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.vnum()).isEqualTo(100);
        assertThat(stats.itemName()).isEqualTo("Miecz +0");
        assertThat(stats.minimumPrice()).isEqualTo(1000L);
        assertThat(stats.meanPrice()).isEqualByComparingTo("1000");
        assertThat(stats.trimmedMeanPrice()).isEqualByComparingTo("1000");
        assertThat(stats.medianPrice()).isEqualByComparingTo("1000");
        assertThat(stats.contributingShopCount()).isEqualTo(1);
        assertThat(stats.rawOfferCount()).isEqualTo(1);
        assertThat(stats.totalQuantity()).isEqualTo(5);
        assertThat(stats.totalPriceLevelCount()).isEqualTo(1);

        // Percentiles for n=1: all percentiles point to index 0
        assertThat(stats.percentiles().p10()).isEqualTo(1000L);
        assertThat(stats.percentiles().p20()).isEqualTo(1000L);
        assertThat(stats.percentiles().p25()).isEqualTo(1000L);
        assertThat(stats.percentiles().p50()).isEqualTo(1000L);
        assertThat(stats.percentiles().p75()).isEqualTo(1000L);
        assertThat(stats.percentiles().p90()).isEqualTo(1000L);

        // Outliers
        assertThat(stats.outliers().totalCount()).isZero();
        assertThat(stats.outliers().lowerCount()).isZero();
        assertThat(stats.outliers().upperCount()).isZero();

        // Buyer reference
        assertThat(stats.buyerReference().price()).isEqualTo(1000L);
        assertThat(stats.buyerReference().shopsAtOrBelow()).isEqualTo(1);
        assertThat(stats.buyerReference().quantityAtOrBelow()).isEqualTo(5);

        // Depth
        assertThat(stats.depth()).hasSize(1);
        assertThat(stats.depth().getFirst().price()).isEqualTo(1000L);
        assertThat(stats.depth().getFirst().quantityAtPrice()).isEqualTo(5);
        assertThat(stats.depth().getFirst().cumulativeQuantity()).isEqualTo(5);
        assertThat(stats.depth().getFirst().shopCountAtPrice()).isEqualTo(1);
        assertThat(stats.depth().getFirst().cumulativeShopCount()).isEqualTo(1);

        // Histogram
        assertThat(stats.histogram()).hasSize(1);
        assertThat(stats.histogram().getFirst().shopCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Single shop with multiple listings and different prices: one shop = one vote at cheapest price")
    void singleShopMultiplePrices() {
        List<RawListing> listings = List.of(
                new RawListing(100, "Miecz +0", 800_000L, 2, "shop-1"),
                new RawListing(100, "Miecz +0", 900_000L, 3, "shop-1"),
                new RawListing(100, "Miecz +0", 1_500_000L, 15, "shop-1")
        );

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(100, listings);

        assertThat(stats).isNotNull();
        // Single shop -> contributingShopCount is 1
        assertThat(stats.contributingShopCount()).isEqualTo(1);
        assertThat(stats.rawOfferCount()).isEqualTo(3);
        assertThat(stats.totalQuantity()).isEqualTo(20);
        assertThat(stats.totalPriceLevelCount()).isEqualTo(3);
        assertThat(stats.minimumPrice()).isEqualTo(800_000L);
        assertThat(stats.medianPrice()).isEqualByComparingTo("800000");

        // Buyer reference (P20 = 800_000)
        assertThat(stats.buyerReference().price()).isEqualTo(800_000L);
        assertThat(stats.buyerReference().shopsAtOrBelow()).isEqualTo(1);
        // Only the 800k listing is <= P20 (qty=2), not all 20 units!
        assertThat(stats.buyerReference().quantityAtOrBelow()).isEqualTo(2);

        // Market Depth has 3 price levels
        assertThat(stats.depth()).hasSize(3);
        assertThat(stats.depth().get(0).price()).isEqualTo(800_000L);
        assertThat(stats.depth().get(0).quantityAtPrice()).isEqualTo(2);
        assertThat(stats.depth().get(0).cumulativeQuantity()).isEqualTo(2);
        assertThat(stats.depth().get(0).cumulativeShopCount()).isEqualTo(1);

        assertThat(stats.depth().get(1).price()).isEqualTo(900_000L);
        assertThat(stats.depth().get(1).quantityAtPrice()).isEqualTo(3);
        assertThat(stats.depth().get(1).cumulativeQuantity()).isEqualTo(5);
        assertThat(stats.depth().get(1).cumulativeShopCount()).isEqualTo(1);

        assertThat(stats.depth().get(2).price()).isEqualTo(1_500_000L);
        assertThat(stats.depth().get(2).quantityAtPrice()).isEqualTo(15);
        assertThat(stats.depth().get(2).cumulativeQuantity()).isEqualTo(20);
        assertThat(stats.depth().get(2).cumulativeShopCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two shops calculate correct discrete percentiles, classical median and trimmed mean")
    void twoShops() {
        List<RawListing> listings = List.of(
                new RawListing(100, "Miecz", 100L, 1, "shop-1"),
                new RawListing(100, "Miecz", 200L, 1, "shop-2")
        );

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(100, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(2);
        assertThat(stats.totalPriceLevelCount()).isEqualTo(2);
        assertThat(stats.minimumPrice()).isEqualTo(100L);
        assertThat(stats.meanPrice()).isEqualByComparingTo("150");
        assertThat(stats.trimmedMeanPrice()).isEqualByComparingTo("150");

        // n=2:
        // P10: ceil(2 * 0.1) = 1 -> index 0 (100)
        // P20: ceil(2 * 0.2) = 1 -> index 0 (100)
        // P25: ceil(2 * 0.25) = 1 -> index 0 (100)
        // P50: ceil(2 * 0.5) = 1 -> index 0 (100)
        // P75: ceil(2 * 0.75) = 2 -> index 1 (200)
        // P90: ceil(2 * 0.9) = 2 -> index 1 (200)
        assertThat(stats.percentiles().p10()).isEqualTo(100L);
        assertThat(stats.percentiles().p20()).isEqualTo(100L);
        assertThat(stats.percentiles().p25()).isEqualTo(100L);
        assertThat(stats.percentiles().p50()).isEqualTo(100L); // Discrete P50
        assertThat(stats.percentiles().p75()).isEqualTo(200L);
        assertThat(stats.percentiles().p90()).isEqualTo(200L);

        // Classical median is (100 + 200) / 2 = 150
        assertThat(stats.medianPrice()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("Four shops distinguish classical median from discrete P50")
    void fourShopsClassicalMedianVsDiscreteP50() {
        List<RawListing> listings = List.of(
                new RawListing(100, "Miecz", 100L, 1, "shop-1"),
                new RawListing(100, "Miecz", 200L, 1, "shop-2"),
                new RawListing(100, "Miecz", 300L, 1, "shop-3"),
                new RawListing(100, "Miecz", 400L, 1, "shop-4")
        );

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(100, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(4);

        // Discrete P50: ceil(4 * 0.5) = 2 -> index 1 -> 200
        assertThat(stats.percentiles().p50()).isEqualTo(200L);

        // Classical median: (200 + 300) / 2 = 250
        assertThat(stats.medianPrice()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("Odd number of shops has identical classical median and discrete P50")
    void oddShopsMedian() {
        List<RawListing> listings = List.of(
                new RawListing(100, "Miecz", 100L, 1, "shop-1"),
                new RawListing(100, "Miecz", 200L, 1, "shop-2"),
                new RawListing(100, "Miecz", 300L, 1, "shop-3")
        );

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(100, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.percentiles().p50()).isEqualTo(200L);
        assertThat(stats.medianPrice()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("48 shops example from specification computes exact discrete percentiles")
    void fortyEightShopsDiscretePercentiles() {
        // Create 48 shops with prices from 800_000 to 1_500_000
        List<RawListing> listings = new ArrayList<>();
        // 10 shops at 800_000 ... 820_000
        for (int i = 1; i <= 10; i++) {
            listings.add(new RawListing(30021, "Kawałek Klejnotu", 800_000L + (i - 1) * 2000L, 2, "shop-" + i));
        }
        // 38 more shops up to 1_500_000
        for (int i = 11; i <= 48; i++) {
            listings.add(new RawListing(30021, "Kawałek Klejnotu", 820_000L + (i - 10) * 18_000L, 1, "shop-" + i));
        }

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(30021, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(48);

        // P20: ceil(48 * 0.20) = ceil(9.6) = 10 -> index 9 (10th cheapest price)
        // 10th price is 800_000 + 9 * 2000 = 818_000
        assertThat(stats.percentiles().p20()).isEqualTo(818_000L);
        assertThat(stats.buyerReference().price()).isEqualTo(818_000L);
        assertThat(stats.buyerReference().shopsAtOrBelow()).isEqualTo(10);
    }

    @Test
    @DisplayName("More than 100 unique price levels preserves total count and buckets depth to 100 with exact totalQuantity")
    void moreThan100PriceLevelsBucketing() {
        // 150 shops with 150 distinct prices
        List<RawListing> listings = new ArrayList<>();
        long expectedTotalQuantity = 0;
        for (int i = 1; i <= 150; i++) {
            long price = 1000L * i;
            int qty = i % 5 + 1;
            expectedTotalQuantity += qty;
            listings.add(new RawListing(500, "Przedmiot 500", price, qty, "shop-" + i));
        }

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(500, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(150);
        assertThat(stats.totalQuantity()).isEqualTo(expectedTotalQuantity);
        assertThat(stats.totalPriceLevelCount()).isEqualTo(150);

        // Depth must be bucketed to exactly 100 points
        List<DepthPoint> depth = stats.depth();
        assertThat(depth).hasSize(100);

        // The final depth point MUST cover the entire supply
        DepthPoint lastPoint = depth.getLast();
        assertThat(lastPoint.price()).isEqualTo(150_000L);
        assertThat(lastPoint.cumulativeQuantity()).isEqualTo(expectedTotalQuantity);
        assertThat(lastPoint.cumulativeShopCount()).isEqualTo(150);

        // Sum of quantityAtPrice across all 100 bucketed points equals expectedTotalQuantity
        long sumQtyAtPrice = depth.stream().mapToLong(DepthPoint::quantityAtPrice).sum();
        assertThat(sumQtyAtPrice).isEqualTo(expectedTotalQuantity);

        // Prices must be strictly monotonic ascending
        for (int i = 0; i < depth.size() - 1; i++) {
            assertThat(depth.get(i).price()).isLessThan(depth.get(i + 1).price());
            assertThat(depth.get(i).cumulativeQuantity()).isLessThanOrEqualTo(depth.get(i + 1).cumulativeQuantity());
        }
    }

    @Test
    @DisplayName("IQR and Outliers detection with extreme values")
    void iqrAndOutliersDetection() {
        // 10 regular shops around 100k - 120k
        // 1 extreme low outlier at 1k
        // 2 extreme high outliers at 500k and 1m
        List<RawListing> listings = new ArrayList<>();
        listings.add(new RawListing(1, "Item", 1_000L, 1, "shop-low"));
        for (int i = 1; i <= 10; i++) {
            listings.add(new RawListing(1, "Item", 100_000L + i * 2000L, 1, "shop-mid-" + i));
        }
        listings.add(new RawListing(1, "Item", 500_000L, 1, "shop-high-1"));
        listings.add(new RawListing(1, "Item", 1_000_000L, 1, "shop-high-2"));

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(1, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(13);
        assertThat(stats.outliers().lowerCount()).isEqualTo(1);
        assertThat(stats.outliers().upperCount()).isEqualTo(2);
        assertThat(stats.outliers().totalCount()).isEqualTo(3);
        assertThat(stats.iqr()).isGreaterThan(0);
        assertThat(stats.relativeIqr()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Trimmed mean excludes top 10% and bottom 10% on large samples")
    void trimmedMeanExcludesExtremes() {
        // 20 shops: 2 at 10, 16 at 100, 2 at 1000
        List<RawListing> listings = new ArrayList<>();
        listings.add(new RawListing(1, "Item", 10L, 1, "s1"));
        listings.add(new RawListing(1, "Item", 10L, 1, "s2"));
        for (int i = 3; i <= 18; i++) {
            listings.add(new RawListing(1, "Item", 100L, 1, "s" + i));
        }
        listings.add(new RawListing(1, "Item", 1000L, 1, "s19"));
        listings.add(new RawListing(1, "Item", 1000L, 1, "s20"));

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(1, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(20);
        // k = floor(20 * 0.10) = 2. Trims first 2 (10, 10) and last 2 (1000, 1000)
        // Remaining 16 prices are all 100. Trimmed mean must be exactly 100.
        assertThat(stats.trimmedMeanPrice()).isEqualByComparingTo("100");
        // Regular mean includes extremes: (20 + 1600 + 2000) / 20 = 3620 / 20 = 181
        assertThat(stats.meanPrice()).isEqualByComparingTo("181");
    }

    @Test
    @DisplayName("Duplicate prices across shops are handled correctly for percentiles and depth")
    void duplicatePricesAcrossShops() {
        // 5 shops with price 100
        // 5 shops with price 200
        List<RawListing> listings = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            listings.add(new RawListing(1, "Item", 100L, 2, "shop-100-" + i));
        }
        for (int i = 1; i <= 5; i++) {
            listings.add(new RawListing(1, "Item", 200L, 3, "shop-200-" + i));
        }

        ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(1, listings);

        assertThat(stats).isNotNull();
        assertThat(stats.contributingShopCount()).isEqualTo(10);
        assertThat(stats.totalQuantity()).isEqualTo(25); // 5*2 + 5*3 = 10 + 15 = 25
        assertThat(stats.totalPriceLevelCount()).isEqualTo(2);

        // P20: ceil(10 * 0.20) = 2 -> index 1 -> price 100
        assertThat(stats.percentiles().p20()).isEqualTo(100L);
        // All 5 shops at 100 are <= P20
        assertThat(stats.buyerReference().shopsAtOrBelow()).isEqualTo(5);
        assertThat(stats.buyerReference().quantityAtOrBelow()).isEqualTo(10);

        // Depth: 2 price points
        assertThat(stats.depth()).hasSize(2);
        assertThat(stats.depth().get(0).price()).isEqualTo(100L);
        assertThat(stats.depth().get(0).quantityAtPrice()).isEqualTo(10);
        assertThat(stats.depth().get(0).cumulativeQuantity()).isEqualTo(10);
        assertThat(stats.depth().get(0).shopCountAtPrice()).isEqualTo(5);
        assertThat(stats.depth().get(0).cumulativeShopCount()).isEqualTo(5);

        assertThat(stats.depth().get(1).price()).isEqualTo(200L);
        assertThat(stats.depth().get(1).quantityAtPrice()).isEqualTo(15);
        assertThat(stats.depth().get(1).cumulativeQuantity()).isEqualTo(25);
        assertThat(stats.depth().get(1).shopCountAtPrice()).isEqualTo(5);
        assertThat(stats.depth().get(1).cumulativeShopCount()).isEqualTo(10);
    }
}
