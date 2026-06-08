package io.casehub.ledger.test;

import java.util.Map;

import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgreSQLTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return Map.of(
                "quarkus.datasource.jdbc.url", container.getJdbcUrl(),
                "quarkus.datasource.username", container.getUsername(),
                "quarkus.datasource.password", container.getPassword());
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
