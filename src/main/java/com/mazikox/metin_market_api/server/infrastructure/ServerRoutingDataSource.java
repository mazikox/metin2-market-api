package com.mazikox.metin_market_api.server.infrastructure;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public final class ServerRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return ServerContext.requireCurrent();
    }
}
