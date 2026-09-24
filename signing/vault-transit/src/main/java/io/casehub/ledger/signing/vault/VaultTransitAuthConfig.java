package io.casehub.ledger.signing.vault;

import java.util.Optional;

public record VaultTransitAuthConfig(
        AuthMethod method,
        Optional<String> token,
        Optional<String> roleId,
        Optional<String> secretId,
        Optional<String> role,
        Optional<String> jwtPath,
        Optional<String> jwt,
        Optional<String> mountPath) {

    public enum AuthMethod { TOKEN, APPROLE, KUBERNETES, JWT }
}
