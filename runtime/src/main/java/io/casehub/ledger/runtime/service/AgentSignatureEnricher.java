package io.casehub.ledger.runtime.service;

import java.security.Signature;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * {@link LedgerEntryEnricher} that signs each entry with the actorId's Ed25519 private key.
 *
 * <p>
 * Signs {@link LedgerMerkleTree#canonicalBytes(LedgerEntry)} — the same canonical form
 * used for Merkle leaf hashes, guaranteeing that the signature covers the exact fields
 * that appear in the tamper-evident chain.
 *
 * <p>
 * No-op when the actor has no configured key pair. Non-fatal — exceptions are swallowed
 * so a key store failure never blocks a ledger write.
 */
@ApplicationScoped
public class AgentSignatureEnricher implements LedgerEntryEnricher {

    private static final Logger LOG = Logger.getLogger(AgentSignatureEnricher.class);

    private final AgentKeyProvider keyProvider;

    @Inject
    public AgentSignatureEnricher(final AgentKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public void enrich(final LedgerEntry entry) {
        if (entry.actorId == null || entry.agentSignature != null) return;
        try {
            keyProvider.signingKey(entry.actorId).ifPresent(sk -> sign(entry, sk));
        } catch (final Exception e) {
            LOG.warnf("AgentSignatureEnricher failed for actor %s — entry will be unsigned: %s",
                    entry.actorId, e.getMessage());
        }
    }

    private static void sign(final LedgerEntry entry, final SigningKey sk) {
        try {
            final byte[] canonical = LedgerMerkleTree.canonicalBytes(entry);
            final Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(sk.keyPair().getPrivate());
            sig.update(canonical);
            entry.agentSignature = sig.sign();
            entry.agentPublicKey = sk.keyPair().getPublic().getEncoded();
            entry.agentKeyRef = sk.keyRef();
        } catch (final Exception e) {
            throw new IllegalStateException("Ed25519 signing failed for actor " + entry.actorId, e);
        }
    }
}
