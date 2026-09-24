package io.casehub.ledger.core.service;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.LedgerMerkleFrontier;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.LedgerMerkleFrontierRepository;
import io.casehub.ledger.core.merkle.LedgerMerkleTree;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationServiceCoreTest {

    @Test
    void treeRootThrowsWhenNoFrontier() {
        var ledgerRepo = mock(LedgerEntryRepository.class);
        var frontierRepo = mock(LedgerMerkleFrontierRepository.class);
        var service = new VerificationServiceCore(ledgerRepo, frontierRepo);

        UUID subjectId = UUID.randomUUID();
        when(frontierRepo.findBySubjectId(subjectId, "t1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.treeRoot(subjectId, "t1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verifyReturnsTrueForConsistentChain() {
        var ledgerRepo = mock(LedgerEntryRepository.class);
        var frontierRepo = mock(LedgerMerkleFrontierRepository.class);
        var service = new VerificationServiceCore(ledgerRepo, frontierRepo);

        UUID subjectId = UUID.randomUUID();
        LedgerEntry entry = stubEntry(subjectId, 1);
        String leafHash = LedgerMerkleTree.leafHash(entry);
        entry.digest = leafHash;

        List<LedgerMerkleFrontier> frontier = LedgerMerkleTree.append(leafHash, new ArrayList<>(), subjectId);

        when(ledgerRepo.findBySubjectId(subjectId, "t1")).thenReturn(List.of(entry));
        when(frontierRepo.findBySubjectId(subjectId, "t1")).thenReturn(frontier);

        assertThat(service.verify(subjectId, "t1")).isTrue();
    }

    @Test
    void verifyReturnsFalseForTamperedDigest() {
        var ledgerRepo = mock(LedgerEntryRepository.class);
        var frontierRepo = mock(LedgerMerkleFrontierRepository.class);
        var service = new VerificationServiceCore(ledgerRepo, frontierRepo);

        UUID subjectId = UUID.randomUUID();
        LedgerEntry entry = stubEntry(subjectId, 1);
        String leafHash = LedgerMerkleTree.leafHash(entry);
        entry.digest = "tampered-" + leafHash;

        List<LedgerMerkleFrontier> frontier = LedgerMerkleTree.append(leafHash, new ArrayList<>(), subjectId);

        when(ledgerRepo.findBySubjectId(subjectId, "t1")).thenReturn(List.of(entry));
        when(frontierRepo.findBySubjectId(subjectId, "t1")).thenReturn(frontier);

        assertThat(service.verify(subjectId, "t1")).isFalse();
    }

    private static LedgerEntry stubEntry(UUID subjectId, int seq) {
        return new LedgerEntry() {{
            this.id = UUID.randomUUID();
            this.subjectId = subjectId;
            this.sequenceNumber = seq;
            this.entryType = LedgerEntryType.EVENT;
            this.actorId = "actor";
            this.actorType = ActorType.AGENT;
            this.occurredAt = Instant.now();
        }};
    }
}
