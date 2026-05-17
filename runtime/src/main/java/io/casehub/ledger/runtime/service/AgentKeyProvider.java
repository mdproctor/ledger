package io.casehub.ledger.runtime.service;

import java.util.Optional;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * SPI: supplies the {@link SigningKey} used to sign a {@link LedgerEntry}
 * on behalf of a given actorId.
 *
 * <p>
 * Return {@link Optional#empty()} for actors that do not participate in
 * bilateral signing — those entries will be persisted unsigned.
 *
 * <p>
 * The {@link SigningKey} carries a self-derived {@code keyRef}
 * ({@code Base64URL(SHA-256(publicKey.getEncoded()))}) that is stored alongside
 * the signature on each entry, enabling key-generation attribution and
 * compromise detection.
 *
 * <p>
 * Implementations must be {@code @ApplicationScoped} CDI beans. The default
 * implementation ({@link ConfiguredAgentKeyProvider}) reads key paths from
 * {@code casehub.ledger.agent-signing.keys.*} config.
 */
public interface AgentKeyProvider {

    /**
     * Returns the signing key for the given actorId, or empty if this
     * actor does not sign ledger entries.
     *
     * @param actorId the actor identity string (e.g. {@code "claude:reviewer@v1"})
     * @return signing key, or empty for unsigned actors
     */
    Optional<SigningKey> signingKey(String actorId);
}
