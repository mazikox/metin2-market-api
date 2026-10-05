package com.mazikox.metin_market_api.market;

import com.mazikox.metin_market_api.shared.web.ApiExceptionHandler;
import com.mazikox.metin_market_api.market.api.ItemPriceStatisticsResponse;
import com.mazikox.metin_market_api.market.api.ItemSearchController;
import com.mazikox.metin_market_api.market.api.SearchPage;
import com.mazikox.metin_market_api.market.application.GetItemStatistics;
import com.mazikox.metin_market_api.market.application.GetItemSuggestions;
import com.mazikox.metin_market_api.market.application.SearchItems;
import org.junit.jupiter.api.BeforeEach;
import com.mazikox.metin_market_api.market.domain.OfferSort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import com.mazikox.metin_market_api.market.domain.ItemFilters;
import com.mazikox.metin_market_api.market.domain.ItemCategory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemSearchControllerTest {

    private static SearchItems searchItems = mock(SearchItems.class);
    private static GetItemSuggestions getItemSuggestions = mock(GetItemSuggestions.class);
    private static GetItemStatistics getItemStatistics = mock(GetItemStatistics.class);

    private MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        public ItemSearchController itemSearchController() {
            return new ItemSearchController(searchItems, getItemSuggestions, getItemStatistics,
                    mock(com.mazikox.metin_market_api.analytics.AnalyticsService.class));
        }

        @Bean
        public ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }

        @Bean
        public static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }
    }

    @BeforeEach
    void setUp() {
        reset(searchItems);
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void searchWithMaxAllowedVnumsReturnsOk() throws Exception {
        when(searchItems.search(anyString(), anyList(), anyInt(), anyInt(), any(OfferSort.class), anyList(), any(ItemFilters.class)))
                .thenReturn(new SearchPage(List.of(), 0, 20, 0));

        String vnums = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "vnum=" + i)
                .collect(Collectors.joining("&"));

        mockMvc.perform(get("/api/v1/items?" + vnums))
                .andExpect(status().isOk());
    }

    @Test
    void forwardsSupportedSortsAndDefaultsToCheapest() throws Exception {
        when(searchItems.search(anyString(), anyList(), anyInt(), anyInt(), any(OfferSort.class), anyList(), any(ItemFilters.class)))
                .thenReturn(new SearchPage(List.of(), 0, 20, 0));
        mockMvc.perform(get("/api/v1/items")).andExpect(status().isOk());
        verify(searchItems).search("", List.of(), 0, 20, OfferSort.PRICE_ASC, List.of(), ItemFilters.empty());
        mockMvc.perform(get("/api/v1/servers/beavium/items?sort=priceDesc")).andExpect(status().isOk());
        verify(searchItems).search("", List.of(), 0, 20, OfferSort.PRICE_DESC, List.of(), ItemFilters.empty());
        mockMvc.perform(get("/api/v1/items?sort=quantity")).andExpect(status().isOk());
        verify(searchItems).search("", List.of(), 0, 20, OfferSort.QUANTITY_DESC, List.of(), ItemFilters.empty());
        mockMvc.perform(get("/api/v1/items?sort=invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void forwardsBonusPresenceAndSignedMinimumAndRejectsMalformedFilters() throws Exception {
        when(searchItems.search(anyString(), anyList(), anyInt(), anyInt(), any(OfferSort.class), anyList(), any(ItemFilters.class)))
                .thenReturn(new SearchPage(List.of(), 0, 20, 0));
        mockMvc.perform(get("/api/v1/items?bonus=72:40&bonus=71:-25&bonus=17")).andExpect(status().isOk());
        verify(searchItems).search("", List.of(), 0, 20, OfferSort.PRICE_ASC, List.of(
                new com.mazikox.metin_market_api.market.domain.BonusFilter(72, 40),
                new com.mazikox.metin_market_api.market.domain.BonusFilter(71, -25),
                new com.mazikox.metin_market_api.market.domain.BonusFilter(17, null)), ItemFilters.empty());
        for (String value : List.of("0", "-1", "17:x", "17:2147483648", "9999999999", "17:10:20", "")) {
            mockMvc.perform(get("/api/v1/items").param("bonus", value)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/items?bonus=17&bonus=17:10")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/items?bonus=1&bonus=2&bonus=3&bonus=4&bonus=5&bonus=6&bonus=7&bonus=8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesAndForwardsCategoryAndInclusiveLevelRange() throws Exception {
        when(searchItems.search(anyString(), anyList(), anyInt(), anyInt(), any(OfferSort.class), anyList(), any(ItemFilters.class)))
                .thenReturn(new SearchPage(List.of(), 0, 20, 0));
        mockMvc.perform(get("/api/v1/items?category=necklaces&minLevel=0&maxLevel=75")).andExpect(status().isOk());
        verify(searchItems).search("", List.of(), 0, 20, OfferSort.PRICE_ASC, List.of(), new ItemFilters(ItemCategory.NECKLACES, 0, 75));
        for (String params : List.of("category=unknown", "minLevel=-1", "maxLevel=-1", "minLevel=80&maxLevel=75", "minLevel=x", "maxLevel=2147483648"))
            mockMvc.perform(get("/api/v1/items?" + params)).andExpect(status().isBadRequest());
    }

    @Test
    void searchExceedingMaxVnumsReturnsBadRequest() throws Exception {
        String vnums = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "vnum=" + i)
                .collect(Collectors.joining("&"));

        mockMvc.perform(get("/api/v1/items?" + vnums))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchWithPageSizeExceeding100ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/items?size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statisticsWithMaxAllowedVnumsReturnsOk() throws Exception {
        when(getItemStatistics.getStatistics(anyList()))
                .thenReturn(new ItemPriceStatisticsResponse(List.of()));

        String vnums = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "vnum=" + i)
                .collect(Collectors.joining("&"));

        mockMvc.perform(get("/api/v1/items/statistics?" + vnums))
                .andExpect(status().isOk());
    }

    @Test
    void statisticsExceedingMaxVnumsReturnsBadRequest() throws Exception {
        String vnums = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "vnum=" + i)
                .collect(Collectors.joining("&"));

        mockMvc.perform(get("/api/v1/items/statistics?" + vnums))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statisticsWithEmptyVnumReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/items/statistics?vnum="))
                .andExpect(status().isBadRequest());
    }
}
