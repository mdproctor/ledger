package io.casehub.ledger.runtime.service;

import java.security.MessageDigest;
import java.util.Base64;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * Two-phase entry signing: key preparation and cryptographic signing.
 *
 * <p>The save pipeline calls these in order:
 * <ol>
 *   <li>{@link #prepareKey(LedgerEntry)} — populates {@code agentPublicKey} and
 *       {@code agentKeyRef} so that enrichers (e.g. {@code ActorIdentityValidationEnricher})
 *       can validate the DID binding against the entry's key material.</li>
 *   <li>enricherPipeline.enrich(entry) — content enrichment</li>
 *   <li>digest = leafHash(entry) — hash computation</li>
 *   <li>{@link #sign(LedgerEntry)} — signs {@code canonicalBytes()} and stores the
 *       signature on the entry.</li>
 * </ol>
 *
 * <p>Both methods are no-ops when the actor has no configured signing key.
 */
@ApplicationScoped
public class AgentEntrySigner {

    private static final Logger LOG = Logger.getLogger(AgentEntrySigner.class);

    private final AgentSigner signer;

    @Inject
    public AgentEntrySigner(final AgentSigner signer) {
        this.signer = signer;
    }

    /**
     * Populate {@code agentPublicKey} and {@code agentKeyRef} on the entry without signing.
     * Called BEFORE enrichment so that identity validation enrichers can check the key.
     *
     * <p>No-op if {@code actorId} is null, key is already prepared, or the actor has no
     * configured signing key.
     */
    public void prepareKey(final LedgerEntry entry) {
        if (entry.actorId == null || entry.agentPublicKey != null) return;
        try {
            // Sign dummy data to obtain the key material from the SPI.
            // The signature itself is discarded — only publicKey and keyRef are kept.
            signer.sign(entry.actorId, new byte[0])
                    .ifPresent(sig -> {
                        entry.agentPublicKey = sig.publicKey();
                        entry.agentKeyRef = sig.keyRef();
                    });
        } catch (final Exception e) {
            LOG.warnf("AgentEntrySigner.prepareKey failed for actor %s: %s",
                    entry.actorId, e.getMessage());
        }
    }

    /**
     * Sign the entry's {@code canonicalBytes()} and store the signature.
     * Called AFTER digest computation. Overwrites any dummy data from {@link #prepareKey}.
     *
     * <p>No-op if {@code actorId} is null, entry is already signed, or the actor has
     * no configured signing key.
     */
    public void sign(final LedgerEntry entry) {
        if (entry.actorId == null || entry.agentSignature != null) return;
        try {
            signer.sign(entry.actorId, entry.canonicalBytes())
                    .ifPresent(sig -> {
                        entry.agentSignature = sig.signature();
                        entry.agentPublicKey = sig.publicKey();
                        entry.agentKeyRef = sig.keyRef();
                    });
        } catch (final Exception e) {
            LOG.warnf("AgentEntrySigner.sign failed for actor %s — entry will be unsigned: %s",
                    entry.actorId, e.getMessage());
        }
    }
}
