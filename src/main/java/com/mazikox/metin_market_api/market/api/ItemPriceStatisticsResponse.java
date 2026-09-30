package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.domain.ItemPriceStatistics;

import java.util.List;

public record ItemPriceStatisticsResponse(List<ItemPriceStatistics> items) {
}
