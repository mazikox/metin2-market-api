package com.mazikox.metin_market_api.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/items")
public class ItemSearchController {
    private final ItemSearchService service;
    private final MarketCatalogService catalogService;

    public ItemSearchController(ItemSearchService service, MarketCatalogService catalogService) {
        this.service = service;
        this.catalogService = catalogService;
    }

    @GetMapping
    public ItemSearchService.SearchPage search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) List<@Min(1) Integer> vnum,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(query.strip(), vnum == null ? List.of() : vnum.stream().distinct().toList(), page, size);
    }

    @GetMapping("/suggestions")
    public ItemSuggestionsResponse suggestions(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) @Min(1) Integer vnum) {
        return catalogService.suggestions(query.strip(), vnum);
    }

    @GetMapping("/statistics")
    public ItemPriceStatisticsResponse statistics(
            @RequestParam("vnum") List<@Min(1) Integer> vnum) {
        return catalogService.statistics(vnum.stream().distinct().toList());
    }
}
