package com.mazikox.metin_market_api.server.infrastructure;

import com.mazikox.metin_market_api.server.domain.GameServer;

import java.util.function.Supplier;

public final class ServerContext {
    private static final ThreadLocal<GameServer> CURRENT = new ThreadLocal<>();

    private ServerContext() {
    }

    public static GameServer requireCurrent() {
        GameServer server = CURRENT.get();
        if (server == null) {
            throw new IllegalStateException("A game server must be selected before database access");
        }
        return server;
    }

    public static Scope use(GameServer server) {
        if (server == null) {
            throw new IllegalArgumentException("Game server cannot be null");
        }
        GameServer previous = CURRENT.get();
        CURRENT.set(server);
        return new Scope(previous);
    }

    public static <T> T withServer(GameServer server, Supplier<T> action) {
        try (Scope ignored = use(server)) {
            return action.get();
        }
    }

    private static void restore(GameServer previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final GameServer previous;
        private boolean closed;

        private Scope(GameServer previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                restore(previous);
                closed = true;
            }
        }
    }
}
