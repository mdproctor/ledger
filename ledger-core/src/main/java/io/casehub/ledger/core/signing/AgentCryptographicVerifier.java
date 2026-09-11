package io.casehub.ledger.core.signing;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.core.model.VerificationResult;

public final class AgentCryptographicVerifier {

    private static final List<String> SUPPORTED_ALGORITHMS =
            List.of("Ed25519", "EC", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private AgentCryptographicVerifier() {}

    public static VerificationResult verifyCryptographic(final LedgerEntry entry) {
        if (entry.agentSignature == null || entry.agentPublicKey == null) {
            return VerificationResult.UNSIGNED;
        }
        try {
            final PublicKey publicKey = decodePublicKey(entry.agentPublicKey);
            final String algorithm = SignatureAlgorithms.signatureAlgorithm(publicKey);
            final Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(publicKey);
            sig.update(entry.canonicalBytes());
            return sig.verify(entry.agentSignature) ? VerificationResult.VALID : VerificationResult.INVALID;
        } catch (final Exception e) {
            return VerificationResult.INVALID;
        }
    }

    private static PublicKey decodePublicKey(final byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException ignored) {
            }
        }
        throw new InvalidKeySpecException("Public key does not match any supported algorithm");
    }
}
