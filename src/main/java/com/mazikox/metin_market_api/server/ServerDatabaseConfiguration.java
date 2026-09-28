package com.mazikox.metin_market_api.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class ServerDatabaseConfiguration {
    @Bean(destroyMethod = "close")
    public ServerDataSources serverDataSources(
            DataSourceProperties properties,
            @Value("${app.database.pool.maximum-size:4}") int maximumPoolSize) {
        return new ServerDataSources(properties, maximumPoolSize);
    }

    @Bean
    @Primary
    public DataSource dataSource(ServerDataSources serverDataSources) {
        return serverDataSources.routingDataSource();
    }
}
