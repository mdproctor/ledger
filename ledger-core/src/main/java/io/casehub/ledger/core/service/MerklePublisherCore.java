package io.casehub.ledger.core.service;

import io.casehub.ledger.core.config.MerkleProperties;
import io.casehub.ledger.core.signing.LedgerPemUtil;
import io.casehub.ledger.core.signing.SignatureAlgorithms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MerklePublisherCore {

    private static final Logger log = Logger.getLogger(MerklePublisherCore.class.getName());

    private final MerkleProperties properties;

    public MerklePublisherCore(MerkleProperties properties) {
        this.properties = properties;
    }

    public static String buildCheckpoint(UUID subjectId, int treeSize, String treeRoot) {
        byte[] rootBytes = hexToBytes(treeRoot);
        return "io.casehub.ledger/v1\n"
                + subjectId + "\n"
                + treeSize + "\n"
                + Base64.getEncoder().encodeToString(rootBytes) + "\n";
    }

    public static byte[] signCheckpoint(String checkpointText, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance(SignatureAlgorithms.signatureAlgorithm(privateKey));
            sig.initSign(privateKey);
            sig.update(checkpointText.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Checkpoint signing failed", e);
        }
    }

    public void publish(UUID subjectId, int treeSize, String treeRoot) {
        if (properties.publish().url().isEmpty()) return;

        try {
            String keyId = properties.publish().keyId();
            String checkpoint = buildCheckpoint(subjectId, treeSize, treeRoot);
            PrivateKey privateKey = LedgerPemUtil.loadPrivateKey(properties.publish().privateKey()
                    .orElseThrow(() -> new IllegalStateException(
                            "casehub.ledger.merkle.publish.private-key required when url is set")));
            byte[] signature = signCheckpoint(checkpoint, privateKey);
            String signed = checkpoint
                    + "\n— " + keyId + " " + Base64.getEncoder().encodeToString(signature);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.publish().url().get()))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(signed))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        log.log(Level.WARNING, "Merkle checkpoint publish failed for subject " + subjectId + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.log(Level.WARNING, "Merkle checkpoint publish error for subject " + subjectId + ": " + e.getMessage());
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
