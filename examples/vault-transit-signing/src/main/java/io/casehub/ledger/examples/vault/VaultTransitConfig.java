package io.casehub.ledger.examples.vault;

import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Vault Transit remote signing example.
 * All keys are under the {@code casehub.ledger.vault-transit} prefix.
 */
@ConfigMapping(prefix = "casehub.ledger.vault-transit")
public interface VaultTransitConfig {

    /**
     * Base URL of the Vault instance, e.g. {@code http://localhost:8200}.
     */
    @WithDefault("http://localhost:8200")
    String address();

    /**
     * Vault authentication token.
     * Production deployments should use AppRole or OIDC instead.
     */
    @WithDefault("root")
    String token();

    /**
     * Map of actorId → Vault Transit key name.
     * Example: {@code casehub.ledger.vault-transit.key-mapping."claude:reviewer@v1"=reviewer-key}
     */
    Map<String, String> keyMapping();

    /**
     * How often to invalidate the public-key cache and re-fetch from Vault.
     * Expressed as a Quarkus duration string, e.g. {@code "5m"}, {@code "24h"}.
     */
    @WithDefault("5m")
    String refreshInterval();
}
