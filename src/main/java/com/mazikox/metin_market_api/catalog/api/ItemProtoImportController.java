package com.mazikox.metin_market_api.catalog.api;

import com.mazikox.metin_market_api.catalog.domain.ItemDefinition;
import com.mazikox.metin_market_api.catalog.domain.ItemProtoParser;
import com.mazikox.metin_market_api.catalog.infrastructure.JdbcItemDefinitions;
import com.mazikox.metin_market_api.server.domain.GameServer;
import com.mazikox.metin_market_api.server.infrastructure.ServerContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
public class ItemProtoImportController {
    private final JdbcItemDefinitions catalog;
    private final Map<GameServer, String> tokens;
    public ItemProtoImportController(JdbcItemDefinitions catalog,
            @Value("${app.scanner-token.pandora}") String pandora,
            @Value("${app.scanner-token.elder}") String elder,
            @Value("${app.scanner-token.beavium}") String beavium) {
        this.catalog = catalog;
        this.tokens = Map.of(GameServer.PANDORA, pandora, GameServer.ELDER, elder, GameServer.BEAVIUM, beavium);
    }

    @PostMapping(value = "/internal/v1/servers/{server}/imports/item-proto", consumes = "text/tab-separated-values")
    public ImportResult importProto(@RequestHeader(value = "X-Scanner-Token", required = false) String token,
                                    @RequestBody String tsv) {
        if (!MessageDigest.isEqual(tokens.get(ServerContext.requireCurrent()).getBytes(StandardCharsets.UTF_8),
                (token == null ? "" : token).getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid scanner token");
        List<ItemDefinition> items;
        try { items = ItemProtoParser.parse(tsv); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        catalog.upsert(items);
        // Preserve raw values for special subtypes such as arrows; report suspicious ranges.
        var warnings = items.stream().filter(i -> i.minAttack() > i.maxAttack()
                || i.minMagicAttack() > i.maxMagicAttack()).map(ItemDefinition::vnum).toList();
        return new ImportResult(items.size(), warnings);
    }
    public record ImportResult(int importedItems, List<Integer> unusualAttackRangeVnums) {}
}
