package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.domain.ItemCategory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/items/category-options", "/api/v1/servers/{server}/items/category-options"})
public class CategoryOptionsController {
    private final JdbcClient jdbc;
    public CategoryOptionsController(JdbcClient jdbc) { this.jdbc = jdbc; }
    private record TypePair(int type, int subtype) {}
    public record CategoryOption(String value, String name) {}
    public record Options(boolean available, List<CategoryOption> categories) {}
    @GetMapping
    public Options get() {
        var types = jdbc.sql("SELECT DISTINCT item_type, item_subtype FROM item_definition")
                .query((rs, row) -> new TypePair(rs.getInt(1), rs.getInt(2))).list();
        var categories = Arrays.stream(ItemCategory.values())
                .filter(c -> types.stream().anyMatch(t -> c.matches(t.type(), t.subtype())))
                .map(c -> new CategoryOption(c.slug(), c.label())).toList();
        return new Options(!types.isEmpty(), categories);
    }
}
