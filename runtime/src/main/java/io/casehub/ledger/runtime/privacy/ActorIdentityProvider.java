package io.casehub.ledger.runtime.privacy;

import java.util.Optional;

import io.casehub.platform.api.identity.ActorType;

/**
 * SPI for pseudonymising actor identities written to the ledger.
 *
 * <p>
 * The default implementation is pass-through — existing consumers see zero behaviour change.
 * Replace with a custom CDI bean to plug in any pseudonymisation strategy.
 * The built-in {@code InternalActorIdentityProvider} activates when
 * {@code casehub.ledger.identity.tokenisation.enabled=true}.
 */
public interface ActorIdentityProvider {

    /**
     * Returns a token to store in place of {@code rawActorId} on write.
     * Creates a new mapping if one does not yet exist.
     * Called on every {@code save()} and {@code saveAttestation()}.
     *
     * <p>Only {@link ActorType#HUMAN} actors (and null actorType as a safe default)
     * are tokenised. Non-human actors (SYSTEM, AGENT) are returned unchanged —
     * they are not natural persons and have no GDPR pseudonymisation obligation.
     *
     * <p>
     * <strong>Reactive constraint:</strong> implementations must be non-blocking. In reactive
     * persistence paths this method is called synchronously on the Vert.x event loop without
     * a worker-pool hop. A blocking implementation (e.g. one backed by a JPA query) will
     * stall the event loop. The built-in implementations are non-blocking. Refs #106.
     *
     * @param rawActorId the real actor identity; may be {@code null}
     * @param actorType the type of actor; {@code null} is treated as potentially human (tokenised)
     * @return token to store, or {@code null} if input is {@code null}
     */
    String tokenise(String rawActorId, ActorType actorType);

    /**
     * Returns the existing token for {@code rawActorId} without creating one.
     * Returns {@code rawActorId} unchanged if no mapping exists.
     * Called on read queries ({@code findByActorId}) to avoid spurious token creation.
     *
     * @param rawActorId the real actor identity; may be {@code null}
     * @return existing token, or {@code rawActorId} if unmapped; {@code null} if input is {@code null}
     */
    String tokeniseForQuery(String rawActorId);

    /**
     * Maps a stored token back to the real identity.
     * Returns {@link Optional#empty()} if the mapping has been severed by erasure
     * or never existed.
     *
     * @param token the stored token
     * @return the real identity, or empty if unresolvable
     */
    Optional<String> resolve(String token);

    /**
     * Severs the token→identity mapping for {@code rawActorId}.
     * After this call, {@link #resolve(String)} for the actor's token returns empty.
     * Ledger entries retaining the token become permanently anonymous.
     *
     * @param rawActorId the real actor identity whose mapping to sever; {@code null} is treated as a no-op
     */
    void erase(String rawActorId);
}
