package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VerificationServiceCore {

    private final LedgerEntryRepository ledgerRepo;
    private final LedgerMerkleFrontierRepository frontierRepo;

    public VerificationServiceCore(LedgerEntryRepository ledgerRepo,
                                   LedgerMerkleFrontierRepository frontierRepo) {
        this.ledgerRepo = ledgerRepo;
        this.frontierRepo = frontierRepo;
    }

    public String treeRoot(UUID subjectId, String tenancyId) {
        List<LedgerMerkleFrontier> frontier = frontierRepo.findBySubjectId(subjectId, tenancyId);
        if (frontier.isEmpty()) {
            throw new IllegalStateException("No entries for subject " + subjectId);
        }
        return LedgerMerkleTree.treeRoot(frontier);
    }

    public InclusionProof inclusionProof(UUID entryId, String tenancyId) {
        LedgerEntry entry = ledgerRepo.findEntryById(entryId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));

        List<LedgerEntry> allForSubject = ledgerRepo.findBySubjectId(entry.subjectId, tenancyId);
        List<String> leafHashes = allForSubject.stream().map(e -> e.digest).toList();
        int k = entry.sequenceNumber - 1;
        String root = treeRoot(entry.subjectId, tenancyId);
        InclusionProof proof = LedgerMerkleTree.inclusionProof(entryId, k, leafHashes.size(), leafHashes);
        return new InclusionProof(entryId, k, leafHashes.size(), proof.leafHash(), proof.siblings(), root);
    }

    public boolean verify(UUID subjectId, String tenancyId) {
        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(subjectId, tenancyId);
        List<LedgerMerkleFrontier> frontier = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            String expected = LedgerMerkleTree.leafHash(entry);
            if (!expected.equals(entry.digest)) return false;
            frontier = LedgerMerkleTree.append(expected, frontier, subjectId);
        }
        if (frontier.isEmpty()) return true;
        String computed = LedgerMerkleTree.treeRoot(frontier);
        String stored = treeRoot(subjectId, tenancyId);
        return computed.equals(stored);
    }
}
