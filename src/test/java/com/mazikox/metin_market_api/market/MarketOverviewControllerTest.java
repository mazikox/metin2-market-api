package com.mazikox.metin_market_api.market;

import com.mazikox.metin_market_api.market.api.MarketOverviewController;
import com.mazikox.metin_market_api.market.application.GetMarketOverview;
import com.mazikox.metin_market_api.market.domain.MarketOverview;
import com.mazikox.metin_market_api.market.domain.MarketOverviewSort;
import com.mazikox.metin_market_api.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketOverviewControllerTest {
    private static final GetMarketOverview overview = mock(GetMarketOverview.class);
    private MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean MarketOverviewController controller() { return new MarketOverviewController(overview, mock(com.mazikox.metin_market_api.analytics.AnalyticsService.class)); }
        @Bean ApiExceptionHandler errors() { return new ApiExceptionHandler(); }
        @Bean static MethodValidationPostProcessor validation() { return new MethodValidationPostProcessor(); }
    }

    @BeforeEach
    void setUp() {
        reset(overview);
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void defaultsToEightCardsAndReturnsAnEmptyMarket() throws Exception {
        when(overview.getOverview(8, MarketOverviewSort.SHOPS)).thenReturn(MarketOverview.empty());
        mockMvc.perform(get("/api/v1/servers/beavium/items/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.observedShopCount").value(0));
        verify(overview).getOverview(8, MarketOverviewSort.SHOPS);
    }

    @Test
    void acceptsMaximumLimitOnLegacyRoute() throws Exception {
        when(overview.getOverview(24, MarketOverviewSort.SHOPS)).thenReturn(MarketOverview.empty());
        mockMvc.perform(get("/api/v1/items/overview?limit=24")).andExpect(status().isOk());
        verify(overview).getOverview(24, MarketOverviewSort.SHOPS);
    }

    @Test
    void acceptsQuantityRankingAndRejectsUnknownSorts() throws Exception {
        when(overview.getOverview(8, MarketOverviewSort.QUANTITY)).thenReturn(MarketOverview.empty());
        mockMvc.perform(get("/api/v1/servers/beavium/items/overview?sort=quantity"))
                .andExpect(status().isOk());
        verify(overview).getOverview(8, MarketOverviewSort.QUANTITY);
        mockMvc.perform(get("/api/v1/servers/beavium/items/overview?sort=price"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidLimits() throws Exception {
        for (String limit : new String[]{"0", "-1", "25", "invalid"}) {
            mockMvc.perform(get("/api/v1/servers/beavium/items/overview?limit=" + limit))
                    .andExpect(status().isBadRequest());
        }
    }
}
