package io.casehub.ledger.memory;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Enables the reactive service tier ({@code casehub.ledger.reactive.enabled=true})
 * and activates {@link InMemoryReactiveLedgerEntryRepository} for tests that verify
 * the reactive delegation path.
 */
public class ReactiveProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "casehub.ledger.reactive.enabled", "true",
                "quarkus.arc.selected-alternatives",
                        String.join(",",
                                "io.casehub.ledger.memory.InMemoryLedgerEntryRepository",
                                "io.casehub.ledger.memory.InMemoryLedgerMerkleFrontierRepository",
                                "io.casehub.ledger.memory.InMemoryActorTrustScoreRepository",
                                "io.casehub.ledger.memory.InMemoryKeyRotationRepository",
                                "io.casehub.ledger.memory.InMemoryAgentSigner",
                                "io.casehub.ledger.memory.InMemoryReactiveLedgerEntryRepository",
                                "io.casehub.ledger.memory.InMemoryReactiveKeyRotationRepository"));
    }
}
