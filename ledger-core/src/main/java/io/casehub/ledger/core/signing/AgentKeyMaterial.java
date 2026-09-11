package io.casehub.ledger.core.signing;

import java.util.Objects;

public record AgentKeyMaterial(byte[] publicKey, String keyRef) {

    public AgentKeyMaterial {
        Objects.requireNonNull(keyRef, "keyRef must not be null");
        publicKey = publicKey.clone();
    }
}
