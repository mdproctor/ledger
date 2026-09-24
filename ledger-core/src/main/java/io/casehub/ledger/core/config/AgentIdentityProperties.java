package io.casehub.ledger.core.config;

import java.util.Map;
import java.util.Optional;

public record AgentIdentityProperties(
        ValidationMode validationMode,
        Map<String, String> dids,
        int didResolverCacheTtlMinutes,
        int credentialCacheTtlMinutes,
        int webResolverTimeoutMs,
        int webResolverMaxResponseBytes,
        ScimProperties scim
) {

    public enum ValidationMode {
        WARN, ENFORCE
    }

    public record ScimProperties(
            Optional<String> endpoint,
            Optional<String> authToken,
            int timeoutMs,
            int cacheTtlMinutes,
            boolean requireHttps
    ) {
        public static ScimProperties defaults() {
            return new ScimProperties(Optional.empty(), Optional.empty(), 5000, 5, true);
        }
    }

    public static AgentIdentityProperties defaults() {
        return new AgentIdentityProperties(
                ValidationMode.WARN, Map.of(), 5, 60, 5000, 1048576,
                ScimProperties.defaults()
        );
    }
}
