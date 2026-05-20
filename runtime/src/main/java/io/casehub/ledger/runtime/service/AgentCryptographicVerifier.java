package io.casehub.ledger.runtime.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.model.VerificationResult;

/**
 * Static Ed25519 signature verification utility.
 * Shared by both the blocking and reactive signature verification beans.
 * Mirrors the {@link LedgerMerkleTree} pattern: pure Java, no IO, no CDI.
 */
final class AgentCryptographicVerifier {

    private static final Logger LOG = Logger.getLogger(AgentCryptographicVerifier.class);

    private AgentCryptographicVerifier() {}

    /**
     * Verifies the Ed25519 signature stored on {@code entry} against its stored
     * public key bytes, using the canonical form defined by
     * {@link LedgerMerkleTree#canonicalBytes(LedgerEntry)}.
     *
     * <p>Does NOT check key compromise windows. Returns:
     * <ul>
     *   <li>{@link VerificationResult#INVALID} if the public key is absent (corrupt record)</li>
     *   <li>{@link VerificationResult#VALID} if the signature verifies</li>
     *   <li>{@link VerificationResult#INVALID} if the signature fails or key data is malformed</li>
     * </ul>
     */
    static VerificationResult verifyCryptographic(final LedgerEntry entry) {
        if (entry.agentPublicKey == null) {
            LOG.warnf("Entry %s has agentSignature but no agentPublicKey — record is corrupt", entry.id);
            return VerificationResult.INVALID;
        }
        try {
            final KeyFactory kf = KeyFactory.getInstance("Ed25519");
            final PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(entry.agentPublicKey));
            final Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pub);
            sig.update(LedgerMerkleTree.canonicalBytes(entry));
            return sig.verify(entry.agentSignature) ? VerificationResult.VALID : VerificationResult.INVALID;
        } catch (final Exception e) {
            LOG.debugf("Ed25519 verify failed for entry %s (%s) — likely corrupt key data or JVM config issue",
                    entry.id, e.getMessage());
            return VerificationResult.INVALID;
        }
    }
}
