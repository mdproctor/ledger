package io.casehub.ledger.runtime.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class LedgerPemUtil {

    private LedgerPemUtil() {}

    static PrivateKey loadPrivateKey(final String pemPath) throws Exception {
        final byte[] keyBytes = decodePem(Files.readString(Path.of(pemPath)), "PRIVATE KEY");
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    static PublicKey loadPublicKey(final String pemPath) throws Exception {
        final byte[] keyBytes = decodePem(Files.readString(Path.of(pemPath)), "PUBLIC KEY");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    static byte[] decodePem(final String pem, final String type) {
        return Base64.getDecoder().decode(
            pem.replace("-----BEGIN " + type + "-----", "")
               .replace("-----END " + type + "-----", "")
               .replaceAll("\\s", ""));
    }
}
