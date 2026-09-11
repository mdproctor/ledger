package io.casehub.ledger.core.signing;

import java.util.Optional;

public interface AgentSigner {

    Optional<AgentSignature> sign(String actorId, byte[] data);

    default Optional<AgentKeyMaterial> keyMaterial(String actorId) {
        return sign(actorId, new byte[0])
                .map(sig -> new AgentKeyMaterial(sig.publicKey(), sig.keyRef()));
    }
}
