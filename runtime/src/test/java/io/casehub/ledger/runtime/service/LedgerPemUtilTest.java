package io.casehub.ledger.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerPemUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void loadPrivateKey_roundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        Path pemFile = writePem(tempDir, "private.pem", "PRIVATE KEY", kp.getPrivate().getEncoded());

        var loaded = LedgerPemUtil.loadPrivateKey(pemFile.toString());

        assertThat(loaded.getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
    }

    @Test
    void loadPublicKey_roundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        Path pemFile = writePem(tempDir, "public.pem", "PUBLIC KEY", kp.getPublic().getEncoded());

        var loaded = LedgerPemUtil.loadPublicKey(pemFile.toString());

        assertThat(loaded.getEncoded()).isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void decodePem_stripsHeadersAndWhitespace() {
        byte[] raw = {1, 2, 3, 4, 5};
        String encoded = Base64.getEncoder().encodeToString(raw);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";

        byte[] decoded = LedgerPemUtil.decodePem(pem, "PRIVATE KEY");

        assertThat(decoded).isEqualTo(raw);
    }

    private static Path writePem(Path dir, String name, String type, byte[] encoded) throws Exception {
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        Path file = dir.resolve(name);
        Files.writeString(file, pem);
        return file;
    }
}
