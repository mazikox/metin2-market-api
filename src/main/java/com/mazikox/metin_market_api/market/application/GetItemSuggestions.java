package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.api.ItemSuggestionsResponse;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.ItemSuggestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GetItemSuggestions {
    private final MarketRepository marketRepository;

    public GetItemSuggestions(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public ItemSuggestionsResponse getSuggestions(String query, Integer vnum) {
        long count = marketRepository.countCatalogItems(query, vnum);
        List<ItemSuggestion> families = marketRepository.findUpgradeFamilies(query, vnum, 20);
        List<ItemSuggestion> items = marketRepository.findCatalogItems(query, vnum, 20);

        List<ItemSuggestion> suggestions = new ArrayList<>(families);
        for (ItemSuggestion item : items) {
            if (suggestions.size() == 20) {
                break;
            }
            suggestions.add(item);
        }
        return new ItemSuggestionsResponse(count, List.copyOf(suggestions));
    }
}
