package com.mazikox.metin_market_api.market.api;

import java.util.List;

public record SearchPage(List<ItemSearchResult> items, int page, int size, long totalElements) {}
