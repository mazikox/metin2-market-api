package com.mazikox.metin_market_api.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/items")
public class ItemSearchController {
    private final ItemSearchService service;

    public ItemSearchController(ItemSearchService service) {
        this.service = service;
    }

    @GetMapping
    public ItemSearchService.SearchPage search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Integer vnum,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(query.strip(), vnum, page, size);
    }
}
