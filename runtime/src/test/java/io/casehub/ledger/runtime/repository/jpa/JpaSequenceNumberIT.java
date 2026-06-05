package io.casehub.ledger.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JpaSequenceNumberIT.Profile.class)
class JpaSequenceNumberIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "sequence-number-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Inject
    EntityManager em;

    private TestEntry newEntry(UUID subjectId) {
        TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "tester";
        return e;
    }

    @Test
    @Transactional
    void assignsContiguousSequenceNumbers() {
        UUID subjectId = UUID.randomUUID();

        LedgerEntry e1 = repo.save(newEntry(subjectId));
        LedgerEntry e2 = repo.save(newEntry(subjectId));
        LedgerEntry e3 = repo.save(newEntry(subjectId));

        assertThat(e1.sequenceNumber).isEqualTo(1);
        assertThat(e2.sequenceNumber).isEqualTo(2);
        assertThat(e3.sequenceNumber).isEqualTo(3);
    }

    @Test
    @Transactional
    void findBySubjectIdReturnsInSequenceOrder() {
        UUID subjectId = UUID.randomUUID();

        repo.save(newEntry(subjectId));
        repo.save(newEntry(subjectId));
        repo.save(newEntry(subjectId));

        List<LedgerEntry> entries = repo.findBySubjectId(subjectId);
        assertThat(entries).extracting(e -> e.sequenceNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @Transactional
    void multiSubjectIsolation() {
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();

        LedgerEntry s1e1 = repo.save(newEntry(subject1));
        LedgerEntry s2e1 = repo.save(newEntry(subject2));
        LedgerEntry s1e2 = repo.save(newEntry(subject1));
        LedgerEntry s2e2 = repo.save(newEntry(subject2));

        assertThat(s1e1.sequenceNumber).isEqualTo(1);
        assertThat(s1e2.sequenceNumber).isEqualTo(2);
        assertThat(s2e1.sequenceNumber).isEqualTo(1);
        assertThat(s2e2.sequenceNumber).isEqualTo(2);
    }

    @Test
    @Transactional
    void overwritesCallerSetSequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        TestEntry entry = newEntry(subjectId);
        entry.sequenceNumber = 999;

        LedgerEntry saved = repo.save(entry);

        assertThat(saved.sequenceNumber).isEqualTo(1);
    }

    @Test
    void rejectsNullSubjectId() {
        TestEntry entry = newEntry(null);
        entry.subjectId = null;

        assertThatThrownBy(() -> repo.save(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subjectId");
    }

    @Test
    @Transactional
    void uniqueConstraintPreventsDuplicateSequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        repo.save(newEntry(subjectId));

        assertThatThrownBy(() -> {
            em.createNativeQuery(
                    "INSERT INTO ledger_entry (id, dtype, subject_id, sequence_number, entry_type, occurred_at) " +
                    "VALUES (?1, 'TEST', ?2, 1, 'EVENT', CURRENT_TIMESTAMP)")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, subjectId)
                    .executeUpdate();
            em.flush();
        }).hasMessageContaining("IDX_LEDGER_ENTRY_SUBJECT_SEQ");
    }

    @Test
    @Transactional
    void healthJobDetectsNoGapsInJpaAssignedSequences() {
        UUID subjectId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            repo.save(newEntry(subjectId));
        }

        List<?> gapResults = em.createQuery(
                "SELECT e.subjectId FROM LedgerEntry e " +
                "WHERE e.subjectId = :sid " +
                "GROUP BY e.subjectId " +
                "HAVING COUNT(e) != MAX(e.sequenceNumber) - MIN(e.sequenceNumber) + 1")
                .setParameter("sid", subjectId)
                .getResultList();

        assertThat(gapResults).isEmpty();
    }

    @Test
    @Transactional
    void leafHashCoversCorrectSequenceNumber() {
        UUID subjectId = UUID.randomUUID();
        LedgerEntry saved = repo.save(newEntry(subjectId));

        String recomputed = LedgerMerkleTree.leafHash(saved);

        assertThat(saved.digest).isEqualTo(recomputed);
        assertThat(saved.sequenceNumber).isEqualTo(1);
    }

    @Test
    @Transactional
    void keyRotationEntryGetsCorrectSequenceNumber() {
        String actorId = "claude:reviewer@v1";
        UUID subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));

        KeyRotationEntry rotation = new KeyRotationEntry();
        rotation.subjectId = subjectId;
        rotation.entryType = LedgerEntryType.EVENT;
        rotation.actorId = actorId;
        rotation.actorType = ActorType.AGENT;
        rotation.actorRole = "reviewer";
        rotation.previousKeyRef = "old-key-ref";
        rotation.newKeyRef = "new-key-ref";
        rotation.reason = KeyRotationReason.SCHEDULED;
        rotation.effectiveSince = Instant.now();

        LedgerEntry saved = repo.save(rotation);

        assertThat(saved.sequenceNumber).isEqualTo(1);
        assertThat(saved).isInstanceOf(KeyRotationEntry.class);
    }
}
