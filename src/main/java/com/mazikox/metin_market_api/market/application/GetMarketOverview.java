package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.MarketOverview;
import com.mazikox.metin_market_api.market.domain.MarketOverviewSort;
import org.springframework.stereotype.Service;

@Service
public class GetMarketOverview {
    private final MarketRepository marketRepository;

    public GetMarketOverview(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public MarketOverview getOverview(int limit, MarketOverviewSort sort) {
        return marketRepository.findMarketOverview(limit, sort);
    }
}
