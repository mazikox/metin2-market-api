package com.mazikox.metin_market_api.market.api;

import com.mazikox.metin_market_api.market.application.GetBonusOptions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/items/bonus-options", "/api/v1/servers/{server}/items/bonus-options"})
public class BonusOptionsController {
    private final GetBonusOptions options;
    public BonusOptionsController(GetBonusOptions options) { this.options = options; }
    @GetMapping
    public List<GetBonusOptions.BonusOption> options() { return options.get(); }
}
