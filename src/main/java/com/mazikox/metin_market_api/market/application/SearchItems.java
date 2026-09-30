package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.api.SearchPage;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.application.port.MarketRepository.MarketListingPage;
import com.mazikox.metin_market_api.market.domain.ItemBonusCatalog;
import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchItems {
    private final MarketRepository marketRepository;

    public SearchItems(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public SearchPage search(String query, List<Integer> vnums, int page, int size) {
        GameServer gameServer = ServerContext.requireCurrent();
        MarketListingPage pageResult = marketRepository.searchListings(query, vnums, page, size);
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
                        listing.observedDate()
                )).toList();
        return new SearchPage(items, page, size, pageResult.totalElements());
    }
}
