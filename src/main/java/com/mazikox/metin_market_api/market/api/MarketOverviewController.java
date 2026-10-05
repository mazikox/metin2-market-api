package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.application.GetMarketOverview;
import com.mazikox.metin_market_api.analytics.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import com.mazikox.metin_market_api.market.domain.MarketOverview;
import com.mazikox.metin_market_api.market.domain.MarketOverviewSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@Validated
@RestController
@RequestMapping({"/api/v1/items/overview", "/api/v1/servers/{server}/items/overview"})
public class MarketOverviewController {
    private final GetMarketOverview getMarketOverview;
    private final AnalyticsService analytics;

    public MarketOverviewController(GetMarketOverview getMarketOverview, AnalyticsService analytics) {
        this.getMarketOverview = getMarketOverview;
        this.analytics = analytics;
    }

    @GetMapping
    public MarketOverview overview(
            HttpServletRequest request,
            @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit,
            @RequestParam(defaultValue = "shops") @Pattern(regexp = "shops|quantity") String sort) {
        MarketOverview result = getMarketOverview.getOverview(limit, MarketOverviewSort.valueOf(sort.toUpperCase(Locale.ROOT)));
        // Opening the market counts as a result view, never as a user search.
        analytics.record(request, 0, "", List.of());
        return result;
    }
}
