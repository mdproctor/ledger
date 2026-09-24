package io.casehub.ledger.core.config;

import java.util.Map;

public record AgentSigningProperties(Map<String, ActorKeyProperties> keys) {

    public record ActorKeyProperties(String privateKey, String publicKey) {}

    public static AgentSigningProperties defaults() {
        return new AgentSigningProperties(Map.of());
    }
}
