package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.application.GetItemStatistics;
import com.mazikox.metin_market_api.market.application.GetItemSuggestions;
import com.mazikox.metin_market_api.market.application.SearchItems;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/api/v1/items", "/api/v1/servers/{server}/items"})
public class ItemSearchController {
    public static final int MAX_VNUMS = 100;
    private final SearchItems searchItems;
    private final GetItemSuggestions getItemSuggestions;
    private final GetItemStatistics getItemStatistics;

    public ItemSearchController(
            SearchItems searchItems,
            GetItemSuggestions getItemSuggestions,
            GetItemStatistics getItemStatistics) {
        this.searchItems = searchItems;
        this.getItemSuggestions = getItemSuggestions;
        this.getItemStatistics = getItemStatistics;
    }

    @GetMapping
    public SearchPage search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) @Size(max = MAX_VNUMS) List<@Min(1) Integer> vnum,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchItems.search(query.strip(), vnum == null ? List.of() : vnum.stream().distinct().toList(), page, size);
    }

    @GetMapping("/suggestions")
    public ItemSuggestionsResponse suggestions(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) @Min(1) Integer vnum) {
        return getItemSuggestions.getSuggestions(query.strip(), vnum);
    }

    @GetMapping("/statistics")
    public ItemPriceStatisticsResponse statistics(
            @RequestParam("vnum") @Size(min = 1, max = MAX_VNUMS) List<@Min(1) Integer> vnum) {
        return getItemStatistics.getStatistics(vnum.stream().distinct().toList());
    }
}
