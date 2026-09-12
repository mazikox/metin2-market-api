package com.mazikox.metin_market_api.market;

import java.util.List;

public record ItemSuggestionsResponse(long totalMatches, List<ItemSuggestion> suggestions) {
}
