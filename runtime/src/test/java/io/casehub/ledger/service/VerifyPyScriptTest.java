package io.casehub.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyPyScriptTest {

    @TempDir Path tempDir;

    @Test
    void validChain_passesVerification() throws Exception {
        final UUID subjectId = UUID.randomUUID();
        final List<LedgerEntry> entries = List.of(
                testEntry(subjectId, "actor-1", 1),
                testEntry(subjectId, "actor-2", 2),
                testEntry(subjectId, "actor-3", 3));

        final String bundleJson = buildBundleJson(subjectId, entries);
        final int exitCode = runVerifyScript(bundleJson);

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void tamperedChain_failsVerification() throws Exception {
        final UUID subjectId = UUID.randomUUID();
        final List<LedgerEntry> entries = List.of(
                testEntry(subjectId, "actor-1", 1),
                testEntry(subjectId, "actor-2", 2));

        String bundleJson = buildBundleJson(subjectId, entries);
        // Replace a known hex digest prefix with a different value to simulate tampering
        final String firstDigest    = LedgerMerkleTree.leafHash(entries.get(0));
        final String tamperedDigest = "ff" + firstDigest.substring(2);
        bundleJson = bundleJson.replace(firstDigest, tamperedDigest);

        final int exitCode = runVerifyScript(bundleJson);

        assertThat(exitCode).isEqualTo(1);
    }

    private String buildBundleJson(final UUID subjectId,
            final List<LedgerEntry> entries) throws Exception {
        final List<Map<String, Object>> chainEntries = new ArrayList<>();
        List<LedgerMerkleFrontier> frontier = new ArrayList<>();
        for (final LedgerEntry entry : entries) {
            final String digest = LedgerMerkleTree.leafHash(entry);
            entry.digest = digest;
            final Map<String, Object> ce = new LinkedHashMap<>();
            ce.put("sequenceNumber", entry.sequenceNumber);
            ce.put("digest", digest);
            chainEntries.add(ce);
            frontier = LedgerMerkleTree.append(digest, frontier, subjectId);
        }
        final String root = LedgerMerkleTree.treeRoot(frontier);

        final List<Map<String, Object>> frontierNodes = frontier.stream()
                .map(f -> {
                    final Map<String, Object> m = new LinkedHashMap<>();
                    m.put("level", f.level);
                    m.put("hash", f.hash);
                    return m;
                })
                .collect(Collectors.toList());

        final Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("subjectId", subjectId.toString());
        chain.put("entries", chainEntries);
        chain.put("frontier", frontierNodes);
        chain.put("storedRoot", root);

        final Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("tenancyId", "test-tenant");
        bundle.put("generatedAt", Instant.now().toString());
        bundle.put("chains", List.of(chain));

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundle);
    }

    private int runVerifyScript(final String bundleJson) throws Exception {
        final Path bundlePath = tempDir.resolve("bundle.json");
        Files.writeString(bundlePath, bundleJson);

        final Path scriptPath = tempDir.resolve("verify.py");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("verification/verify.py")) {
            assertThat(is).as("verify.py must be on classpath").isNotNull();
            Files.write(scriptPath, is.readAllBytes());
        }

        final ProcessBuilder pb = new ProcessBuilder(
                "python3", scriptPath.toString(), bundlePath.toString());
        pb.redirectErrorStream(true);
        final Process process = pb.start();
        final String output = new String(
                process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final int exitCode = process.waitFor();

        System.out.println("verify.py output:\n" + output);
        return exitCode;
    }

    private static TestEntry testEntry(final UUID subjectId,
            final String actorId, final int seq) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "Verifier";
        e.occurredAt = Instant.now();
        e.tenancyId = "test-tenant";
        return e;
    }
}
