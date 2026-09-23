package io.casehub.ledger.runtime.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.ChainEntry;
import io.casehub.ledger.core.compliance.FrontierNode;
import io.casehub.ledger.core.compliance.SubjectChain;
import io.casehub.ledger.core.compliance.VerificationBundle;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;

@ApplicationScoped
public class MerkleVerificationBundleService {

    @Inject
    LedgerEntryRepository repo;

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    @Inject
    LedgerVerificationService verificationService;

    @Transactional
    public VerificationBundle generateBundle(final String tenancyId) {
        final List<UUID> subjectIds = repo.findDistinctSubjectIds(tenancyId);
        final List<SubjectChain> chains = subjectIds.stream()
                .map(sid -> buildChain(sid, tenancyId))
                .toList();
        final String script = loadResource("verification/verify.py");
        final String instructions = loadResource("verification/VERIFY-README.md");
        return new VerificationBundle(tenancyId, Instant.now(), chains, script, instructions);
    }

    private SubjectChain buildChain(final UUID subjectId, final String tenancyId) {
        final List<LedgerEntry> entries = repo.findBySubjectId(subjectId, tenancyId);
        final List<ChainEntry> chainEntries = entries.stream()
                .map(e -> new ChainEntry(e.sequenceNumber, e.digest))
                .toList();
        final List<LedgerMerkleFrontier> frontier =
                frontierRepo.findBySubjectId(subjectId, tenancyId);
        final List<FrontierNode> frontierNodes = frontier.stream()
                .map(f -> new FrontierNode(f.level, f.hash))
                .toList();
        String storedRoot;
        try {
            storedRoot = verificationService.treeRoot(subjectId, tenancyId);
        } catch (final Exception e) {
            storedRoot = null;
        }
        return new SubjectChain(subjectId, chainEntries, frontierNodes, storedRoot);
    }

    private String loadResource(final String name) {
        try (var is = getClass().getClassLoader().getResourceAsStream(name)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            return "";
        }
    }
}
