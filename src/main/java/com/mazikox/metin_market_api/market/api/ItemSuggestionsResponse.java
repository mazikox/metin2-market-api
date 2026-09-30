package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.domain.ItemSuggestion;

import java.util.List;

public record ItemSuggestionsResponse(long totalMatches, List<ItemSuggestion> suggestions) {
}
