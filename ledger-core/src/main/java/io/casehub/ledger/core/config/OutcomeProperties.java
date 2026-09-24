package io.casehub.ledger.core.config;

import io.casehub.platform.api.identity.ActorType;

import java.util.Optional;

public record OutcomeProperties(Optional<String> defaultAttestorId, ActorType defaultAttestorType) {
    public static OutcomeProperties defaults() {
        return new OutcomeProperties(Optional.empty(), ActorType.SYSTEM);
    }
}
