package io.casehub.ledger.runtime.service;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.model.VerificationResult;

/**
 * Algorithm-transparent signature verification utility.
 * Shared by both the blocking and reactive signature verification beans.
 * Mirrors the {@link LedgerMerkleTree} pattern: pure Java, no IO, no CDI.
 *
 * <p>The signing algorithm is detected from the stored X.509 public key bytes
 * (SubjectPublicKeyInfo DER embeds the algorithm OID), so entries signed with
 * Ed25519 today remain verifiable alongside entries signed with ML-DSA or any
 * other future algorithm on the supported list.
 */
final class AgentCryptographicVerifier {

    private static final Logger LOG = Logger.getLogger(AgentCryptographicVerifier.class);

    // Trial order: Ed25519 first (current default), ML-DSA variants when BouncyCastle ≥ 1.79 is present.
    // NoSuchAlgorithmException for unsupported entries is caught silently — forward-compatible.
    private static final List<String> SUPPORTED_ALGORITHMS =
            List.of("Ed25519", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private AgentCryptographicVerifier() {}

    /**
     * Verifies the agent signature stored on {@code entry} against its stored
     * public key bytes, using the canonical form defined by
     * {@link LedgerEntry#canonicalBytes()}.
     *
     * <p>The signing algorithm is detected from the stored public key bytes — no
     * algorithm is assumed. Does NOT check key compromise windows. Returns:
     * <ul>
     *   <li>{@link VerificationResult#INVALID} if the public key is absent (corrupt record)</li>
     *   <li>{@link VerificationResult#VALID} if the signature verifies</li>
     *   <li>{@link VerificationResult#INVALID} if the signature fails, key data is malformed,
     *       or the algorithm is not in the supported list</li>
     * </ul>
     */
    static VerificationResult verifyCryptographic(final LedgerEntry entry) {
        if (entry.agentPublicKey == null) {
            LOG.warnf("Entry %s has agentSignature but no agentPublicKey — record is corrupt", entry.id);
            return VerificationResult.INVALID;
        }
        try {
            final PublicKey pub = loadPublicKey(entry.agentPublicKey);
            final Signature sig = Signature.getInstance(pub.getAlgorithm());
            sig.initVerify(pub);
            sig.update(entry.canonicalBytes());
            return sig.verify(entry.agentSignature) ? VerificationResult.VALID : VerificationResult.INVALID;
        } catch (final Exception e) {
            LOG.debugf("Signature verify failed for entry %s (%s) — corrupt key data or unsupported algorithm",
                    entry.id, e.getMessage());
            return VerificationResult.INVALID;
        }
    }

    private static PublicKey loadPublicKey(final byte[] encoded)
            throws InvalidKeyException {
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // algorithm not supported by this JVM or bytes don't match — try next
            }
        }
        throw new InvalidKeyException(
                "Public key bytes do not match any supported algorithm: " + SUPPORTED_ALGORITHMS);
    }
}
