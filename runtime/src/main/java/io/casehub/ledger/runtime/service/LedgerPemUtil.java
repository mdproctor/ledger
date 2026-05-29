package io.casehub.ledger.runtime.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

public final class LedgerPemUtil {

    // Mirrors AgentCryptographicVerifier.SUPPORTED_ALGORITHMS — update both together.
    private static final List<String> SUPPORTED_ALGORITHMS =
            List.of("Ed25519", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private LedgerPemUtil() {}

    static PrivateKey loadPrivateKey(final String pemPath) throws Exception {
        final byte[] keyBytes = decodePem(Files.readString(Path.of(pemPath)), "PRIVATE KEY");
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePrivate(spec);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // algorithm not supported by this JVM or bytes don't match — try next
            }
        }
        throw new InvalidKeyException(
                "Private key PEM does not match any supported algorithm: " + SUPPORTED_ALGORITHMS);
    }

    static PublicKey loadPublicKey(final String pemPath) throws Exception {
        final byte[] keyBytes = decodePem(Files.readString(Path.of(pemPath)), "PUBLIC KEY");
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // algorithm not supported by this JVM or bytes don't match — try next
            }
        }
        throw new InvalidKeyException(
                "Public key PEM does not match any supported algorithm: " + SUPPORTED_ALGORITHMS);
    }

    /**
     * Parses a PEM-encoded public key from a string (e.g. a Vault Transit API response).
     * Trial-loads through supported algorithms, same as {@link #loadPublicKey(String)}.
     *
     * @param pemContent PEM string containing {@code -----BEGIN PUBLIC KEY-----} header
     * @return parsed public key
     * @throws InvalidKeyException if no supported algorithm recognises the key
     */
    public static PublicKey parsePublicKey(final String pemContent) throws Exception {
        final byte[] keyBytes = decodePem(pemContent, "PUBLIC KEY");
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (final NoSuchAlgorithmException | InvalidKeySpecException ignored) {
            }
        }
        throw new InvalidKeyException(
                "Public key PEM does not match any supported algorithm: " + SUPPORTED_ALGORITHMS);
    }

    static byte[] decodePem(final String pem, final String type) {
        return Base64.getDecoder().decode(
            pem.replace("-----BEGIN " + type + "-----", "")
               .replace("-----END " + type + "-----", "")
               .replaceAll("\\s", ""));
    }
}
