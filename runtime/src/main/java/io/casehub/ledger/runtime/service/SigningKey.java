package io.casehub.ledger.runtime.service;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * A signing key pair with a self-derived stable identifier.
 *
 * <p>
 * The {@code keyRef} is {@code Base64URL(SHA-256(publicKey.getEncoded()))} —
 * derived entirely from the public key bytes. Zero operator configuration.
 * Any party with the public key can independently compute and verify the keyRef.
 * Old entries can retroactively derive their keyRef from stored {@code agentPublicKey} bytes.
 */
public record SigningKey(String keyRef, KeyPair keyPair) {

    public static SigningKey of(final KeyPair keyPair) {
        try {
            final byte[] encoded = keyPair.getPublic().getEncoded();
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(encoded);
            final String keyRef = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return new SigningKey(keyRef, keyPair);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to derive keyRef from public key", e);
        }
    }
}
