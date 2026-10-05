package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.api.ItemMetadata;
import com.mazikox.metin_market_api.market.api.SearchPage;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.OfferSort;
import com.mazikox.metin_market_api.market.application.port.MarketRepository.MarketListingPage;
import com.mazikox.metin_market_api.market.domain.ItemBonusCatalog;
import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.springframework.stereotype.Service;

import java.util.List;
import com.mazikox.metin_market_api.market.domain.ItemFilters;
import com.mazikox.metin_market_api.market.domain.BonusFilter;
import com.mazikox.metin_market_api.catalog.infrastructure.JdbcItemDefinitions;

@Service
public class SearchItems {
    private final MarketRepository marketRepository;

    private final JdbcItemDefinitions catalog;

    public SearchItems(MarketRepository marketRepository, JdbcItemDefinitions catalog) {
        this.marketRepository = marketRepository;
        this.catalog = catalog;
    }

    public SearchPage search(String query, List<Integer> vnums, int page, int size, OfferSort sort, List<BonusFilter> bonuses, ItemFilters itemFilters) {
        GameServer gameServer = ServerContext.requireCurrent();
        MarketListingPage pageResult = marketRepository.searchListings(query, vnums, page, size, sort, bonuses, itemFilters);
        var definitions = catalog.findByVnums(pageResult.items().stream().map(l -> l.itemVnum()).distinct().toList());
        List<ItemSearchResult> items = pageResult.items().stream()
                .map(listing -> new ItemSearchResult(
                        listing.listingId(),
                        listing.itemVnum(),
                        listing.itemName(),
                        listing.quantity(),
                        listing.priceRaw(),
                        listing.unitPrice(),
                        listing.totalQuantity(),
                        listing.totalPrice(),
                        listing.listingCount(),
                        listing.attributes().stream().map(attr -> {
                            ItemBonusCatalog.Details details = ItemBonusCatalog.describe(
                                    gameServer, attr.type(), attr.value());
                            return new ItemSearchResult.ItemAttribute(
                                    attr.slotIndex(),
                                    attr.type(),
                                    details.code(),
                                    details.name(),
                                    attr.value(),
                                    details.displayValue()
                            );
                        }).toList(),
                        listing.sockets(),
                        listing.shop(),
                        listing.observedDate(),
                        ItemMetadata.from(definitions.get(listing.itemVnum()), gameServer)
                )).toList();
        return new SearchPage(items, page, size, pageResult.totalElements());
    }
}
