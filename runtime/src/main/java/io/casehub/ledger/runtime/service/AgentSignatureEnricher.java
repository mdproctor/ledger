package io.casehub.ledger.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * {@link LedgerEntryEnricher} that signs each entry via the configured {@link AgentSigner}.
 *
 * <p>Signs {@link LedgerMerkleTree#canonicalBytes(LedgerEntry)} — the same canonical form
 * used for Merkle leaf hashes, so the signature covers exactly the tamper-evident fields.
 *
 * <p>No-op when the actor has no configured signing key. Non-fatal — exceptions from
 * the signer are swallowed so a key store failure never blocks a ledger write.
 */
@ApplicationScoped
public class AgentSignatureEnricher implements LedgerEntryEnricher {

    private static final Logger LOG = Logger.getLogger(AgentSignatureEnricher.class);

    private final AgentSigner signer;

    @Inject
    public AgentSignatureEnricher(final AgentSigner signer) {
        this.signer = signer;
    }

    @Override
    public void enrich(final LedgerEntry entry) {
        if (entry.actorId == null || entry.agentSignature != null) return;
        try {
            signer.sign(entry.actorId, LedgerMerkleTree.canonicalBytes(entry))
                    .ifPresent(sig -> {
                        entry.agentSignature = sig.signature();
                        entry.agentPublicKey = sig.publicKey();
                        entry.agentKeyRef = sig.keyRef();
                    });
        } catch (final Exception e) {
            LOG.warnf("AgentSignatureEnricher failed for actor %s — entry will be unsigned: %s",
                    entry.actorId, e.getMessage());
        }
    }
}
