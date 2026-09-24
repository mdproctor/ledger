package io.casehub.ledger.core.config;

import java.util.Optional;

public record MerkleProperties(PublishProperties publish) {

    public record PublishProperties(Optional<String> url, Optional<String> privateKey, String keyId) {
        public static PublishProperties defaults() {
            return new PublishProperties(Optional.empty(), Optional.empty(), "default");
        }
    }

    public static MerkleProperties defaults() {
        return new MerkleProperties(PublishProperties.defaults());
    }
}
