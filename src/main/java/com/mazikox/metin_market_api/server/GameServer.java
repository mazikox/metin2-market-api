package com.mazikox.metin_market_api.server;

import java.util.Arrays;
import java.util.Optional;

public enum GameServer {
    PANDORA("pandora"),
    ELDER("elder"),
    BEAVIUM("beavium");

    private final String slug;

    GameServer(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public String schema() {
        return slug;
    }

    public static Optional<GameServer> fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(server -> server.slug.equals(slug))
                .findFirst();
    }
}
