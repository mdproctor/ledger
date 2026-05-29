package io.casehub.ledger.examples.vault;

import java.security.PublicKey;

/**
 * Per-actorId context cached by {@link VaultTransitAgentSigner}.
 * Holds the Vault Transit key name and the public key fetched from Vault.
 * The private key never leaves Vault.
 */
public record VaultTransitContext(String keyName, PublicKey publicKey) {
}
