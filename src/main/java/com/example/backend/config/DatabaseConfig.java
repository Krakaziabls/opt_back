package com.example.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final Environment env;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty("spring.datasource.url"));
        config.setUsername(env.getProperty("spring.datasource.username"));
        config.setPassword(env.getProperty("spring.datasource.password"));
        config.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));

        config.setMaximumPoolSize(env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10));
        config.setMinimumIdle(env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 5));
        config.setIdleTimeout(env.getProperty("spring.datasource.hikari.idle-timeout", Long.class, 600000L));
        config.setConnectionTimeout(env.getProperty("spring.datasource.hikari.connection-timeout", Long.class, 30000L));

        return new HikariDataSource(config);
    }
}
