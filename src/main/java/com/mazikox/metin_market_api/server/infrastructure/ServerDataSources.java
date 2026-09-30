package com.mazikox.metin_market_api.server.infrastructure;

import com.mazikox.metin_market_api.server.domain.GameServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ServerDataSources implements AutoCloseable {
    private final Map<GameServer, HikariDataSource> pools = new EnumMap<>(GameServer.class);
    private final ServerRoutingDataSource routingDataSource;

    public ServerDataSources(DataSourceProperties properties, int maximumPoolSize) {
        try {
            for (GameServer server : GameServer.values()) {
                HikariConfig config = new HikariConfig();
                config.setPoolName("metin-market-" + server.slug());
                config.setJdbcUrl(properties.getUrl());
                config.setUsername(properties.getUsername());
                config.setPassword(properties.getPassword());
                config.setMaximumPoolSize(maximumPoolSize);
                config.setMinimumIdle(0);
                config.addDataSourceProperty("currentSchema", server.schema());

                HikariDataSource pool = new HikariDataSource(config);
                pools.put(server, pool);
                if (server == GameServer.PANDORA) {
                    rejectUnmigratedPublicMarket(pool);
                }
                migrate(pool, server);
            }

            Map<Object, Object> targets = new HashMap<>();
            pools.forEach(targets::put);

            routingDataSource = new ServerRoutingDataSource();
            routingDataSource.setTargetDataSources(targets);
            routingDataSource.setLenientFallback(false);
            routingDataSource.afterPropertiesSet();
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public DataSource routingDataSource() {
        return routingDataSource;
    }

    private static void migrate(DataSource dataSource, GameServer server) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(server.schema())
                .defaultSchema(server.schema())
                .createSchemas(true)
                .load()
                .migrate();
    }

    private static void rejectUnmigratedPublicMarket(DataSource dataSource) {
        String query = """
                SELECT (
                    to_regclass('public.synchronization_batch') IS NOT NULL
                    OR to_regclass('public.scan_run') IS NOT NULL
                    OR to_regclass('public.shop_observation') IS NOT NULL
                    OR to_regclass('public.shop_listing') IS NOT NULL
                    OR to_regclass('public.shop_listing_attribute') IS NOT NULL
                    OR to_regclass('public.shop_listing_socket') IS NOT NULL
                    OR to_regclass('public.flyway_schema_history') IS NOT NULL
                )
                """;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(query);
             var result = statement.executeQuery()) {
            if (result.next() && result.getBoolean(1)) {
                throw new IllegalStateException(
                        "Market tables are still present in public; run ops/migrate-public-to-pandora.sql before starting this API");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not inspect the legacy market schema", exception);
        }
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
    }
}
