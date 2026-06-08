package io.casehub.ledger.test;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public abstract class PostgreSQLTestProfile implements QuarkusTestProfile {

    // db-kind is BUILD_TIME — must live here (triggers re-augmentation), not in the test resource
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.datasource.db-kind", "postgresql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(PostgreSQLTestResource.class));
    }
}
