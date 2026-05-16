package io.casehub.ledger.runtime.service;

import java.security.KeyPair;
import java.util.Optional;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * SPI: supplies the Ed25519 {@link KeyPair} used to sign a {@link LedgerEntry}
 * on behalf of a given actorId.
 *
 * <p>
 * Return {@link Optional#empty()} for actors that do not participate in
 * bilateral signing — those entries will be persisted unsigned.
 *
 * <p>
 * Implementations must be {@code @ApplicationScoped} CDI beans. The default
 * implementation ({@link ConfiguredAgentKeyProvider}) reads key paths from
 * {@code casehub.ledger.agent-signing.keys.*} config.
 */
public interface AgentKeyProvider {

    /**
     * Returns the signing key pair for the given actorId, or empty if this
     * actor does not sign ledger entries.
     *
     * @param actorId the actor identity string (e.g. {@code "claude:reviewer@v1"})
     * @return signing key pair, or empty for unsigned actors
     */
    Optional<KeyPair> signingKeyPair(String actorId);
}
