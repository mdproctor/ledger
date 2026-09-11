package io.casehub.ledger.core.signing;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;

public record AgentSignature(byte[] signature, byte[] publicKey, String keyRef) {

    public AgentSignature {
        Objects.requireNonNull(keyRef, "keyRef must not be null");
        signature = signature.clone();
        publicKey = publicKey.clone();
    }

    public static String computeKeyRef(final byte[] publicKeyEncoded) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static AgentSignature signWith(final KeyPair keyPair, final byte[] data) {
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        Objects.requireNonNull(data, "data must not be null");
        try {
            final byte[] pubEncoded = keyPair.getPublic().getEncoded();
            final Signature sig = Signature.getInstance(SignatureAlgorithms.signatureAlgorithm(keyPair.getPrivate()));
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            final byte[] sigBytes = sig.sign();
            final String keyRef = computeKeyRef(pubEncoded);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Local signing failed", e);
        }
    }
}
