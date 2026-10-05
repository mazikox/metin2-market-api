package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.ItemBonusCatalog;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GetBonusOptions {
    private final MarketRepository repository;
    public GetBonusOptions(MarketRepository repository) { this.repository = repository; }
    public record BonusOption(int type, String code, String name, String unit) {}
    public List<BonusOption> get() {
        var server = ServerContext.requireCurrent();
        return repository.findAvailableBonusTypes().stream().map(type -> {
            var details = ItemBonusCatalog.describe(server, type, 1);
            return new BonusOption(type, details.code(),
                    details.code().equals("UNKNOWN") ? "Nieznany bonus #" + type : details.name(),
                    ItemBonusCatalog.unit(server, type));
        }).toList();
    }
}
