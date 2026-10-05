package com.mazikox.metin_market_api.market.application.port;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatisticsCalculator.RawListing;
import com.mazikox.metin_market_api.market.domain.ItemSuggestion;
import com.mazikox.metin_market_api.market.domain.MarketOverview;
import com.mazikox.metin_market_api.market.domain.OfferSort;
import com.mazikox.metin_market_api.market.domain.MarketOverviewSort;

import com.mazikox.metin_market_api.market.domain.BonusFilter;
import java.time.LocalDate;
import java.util.List;
import com.mazikox.metin_market_api.market.domain.ItemFilters;

public interface MarketRepository {

    record RawItemAttribute(int slotIndex, int type, int value) {}

    record MarketListingRecord(
            long listingId,
            int itemVnum,
            String itemName,
            int quantity,
            long priceRaw,
            long unitPrice,
            long totalQuantity,
            long totalPrice,
            int listingCount,
            List<RawItemAttribute> attributes,
            List<ItemSearchResult.ItemSocket> sockets,
            ItemSearchResult.Shop shop,
            LocalDate observedDate
    ) {}

    record MarketListingPage(List<MarketListingRecord> items, long totalElements) {}

    MarketListingPage searchListings(String query, List<Integer> vnums, int page, int size, OfferSort sort, List<BonusFilter> bonuses, ItemFilters itemFilters);

    List<Integer> findAvailableBonusTypes();

    long countCatalogItems(String query, Integer vnum);

    List<ItemSuggestion> findUpgradeFamilies(String query, Integer vnum, int limit);

    List<ItemSuggestion> findCatalogItems(String query, Integer vnum, int limit);

    List<RawListing> findCanonicalListings(List<Integer> vnums);

    MarketOverview findMarketOverview(int limit, MarketOverviewSort sort);
}
