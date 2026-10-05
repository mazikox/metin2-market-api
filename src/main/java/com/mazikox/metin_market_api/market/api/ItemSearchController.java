package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.analytics.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import com.mazikox.metin_market_api.market.application.GetItemStatistics;
import com.mazikox.metin_market_api.market.application.GetItemSuggestions;
import com.mazikox.metin_market_api.market.application.SearchItems;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.mazikox.metin_market_api.market.domain.OfferSort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.mazikox.metin_market_api.market.domain.ItemFilters;
import com.mazikox.metin_market_api.market.domain.ItemCategory;
import com.mazikox.metin_market_api.market.domain.BonusFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Validated
@RestController
@RequestMapping({"/api/v1/items", "/api/v1/servers/{server}/items"})
public class ItemSearchController {
    private final AnalyticsService analytics;

    public static final int MAX_VNUMS = 100;
    private final SearchItems searchItems;
    private final GetItemSuggestions getItemSuggestions;
    private final GetItemStatistics getItemStatistics;

    public ItemSearchController(
            SearchItems searchItems,
            GetItemSuggestions getItemSuggestions,
            GetItemStatistics getItemStatistics,
            AnalyticsService analytics) {
        this.analytics = analytics;
        this.searchItems = searchItems;
        this.getItemSuggestions = getItemSuggestions;
        this.getItemStatistics = getItemStatistics;
    }

    @GetMapping
    public SearchPage search(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) @Size(max = MAX_VNUMS) List<@Min(1) Integer> vnum,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "priceAsc") @Pattern(regexp = "priceAsc|priceDesc|quantity") String sort,
            @RequestParam(required = false) @Size(max = BonusFilter.MAX_FILTERS) List<String> bonus,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @Min(0) Integer minLevel,
            @RequestParam(required = false) @Min(0) Integer maxLevel) {
        List<Integer> selected = vnum == null ? List.of() : vnum.stream().distinct().toList();
        OfferSort offerSort = switch (sort) {
            case "priceAsc" -> OfferSort.PRICE_ASC;
            case "priceDesc" -> OfferSort.PRICE_DESC;
            case "quantity" -> OfferSort.QUANTITY_DESC;
            default -> throw new IllegalArgumentException("Invalid offer sort");
        };
        List<BonusFilter> bonuses;
        try {
            String[] rawBonuses = request.getParameterValues("bonus");
            if (rawBonuses != null && java.util.Arrays.stream(rawBonuses).anyMatch(String::isBlank))
                throw new IllegalArgumentException("Empty bonus condition");
            bonuses = BonusFilter.parse(bonus);
        }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid bonus filters");
        }
        ItemFilters itemFilters;
        try { itemFilters = new ItemFilters(ItemCategory.parse(category), minLevel, maxLevel); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category or level range");
        }
        SearchPage results = searchItems.search(query.strip(), selected, page, size, offerSort, bonuses, itemFilters);
        analytics.record(request, page, query.strip(), selected);
        return results;
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
