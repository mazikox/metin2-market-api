package com.mazikox.metin_market_api.market;

import com.mazikox.metin_market_api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemSearchControllerTest {

    private static ItemSearchService itemSearchService = mock(ItemSearchService.class);
    private static MarketCatalogService marketCatalogService = mock(MarketCatalogService.class);

    private MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        public ItemSearchController itemSearchController() {
            return new ItemSearchController(itemSearchService, marketCatalogService);
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
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new org.springframework.mock.web.MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void searchWithMaxAllowedVnumsReturnsOk() throws Exception {
        when(itemSearchService.search(anyString(), anyList(), anyInt(), anyInt()))
                .thenReturn(new ItemSearchService.SearchPage(List.of(), 0, 20, 0));

        String vnums = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "vnum=" + i)
                .collect(Collectors.joining("&"));

        mockMvc.perform(get("/api/v1/items?" + vnums))
                .andExpect(status().isOk());
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
        when(marketCatalogService.statistics(anyList()))
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
