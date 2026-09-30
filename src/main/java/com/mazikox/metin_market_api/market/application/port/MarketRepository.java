package com.mazikox.metin_market_api.market.application.port;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.domain.ItemSuggestion;

import java.time.LocalDate;
import java.util.List;

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

    record ShopPrice(int vnum, String itemName, long price, long rawOfferCount, long totalQuantity) {}

    MarketListingPage searchListings(String query, List<Integer> vnums, int page, int size);

    long countCatalogItems(String query, Integer vnum);

    List<ItemSuggestion> findUpgradeFamilies(String query, Integer vnum, int limit);

    List<ItemSuggestion> findCatalogItems(String query, Integer vnum, int limit);

    List<ShopPrice> findShopPrices(List<Integer> vnums);
}
