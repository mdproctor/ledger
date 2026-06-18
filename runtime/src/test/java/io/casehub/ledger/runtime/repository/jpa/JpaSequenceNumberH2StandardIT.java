package io.casehub.ledger.runtime.repository.jpa;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Proves that {@link LedgerSequenceAllocator} works with plain H2 (no {@code MODE=PostgreSQL}).
 *
 * <p>This is a regression guard for casehubio/ledger#150: downstream modules that consume
 * {@code casehub-ledger} and run tests with a default H2 datasource (e.g.
 * {@code casehub-engine-ledger}) must not fail with an {@code ON CONFLICT DO NOTHING} syntax
 * error. The allocator must detect the H2 dialect and use the SQL-standard MERGE path instead.
 */
@QuarkusTest
@TestProfile(JpaSequenceNumberH2StandardIT.Profile.class)
class JpaSequenceNumberH2StandardIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "sequence-number-h2-standard-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Test
    void sequentialSavesProduceContiguousSequenceNumbers_inH2StandardMode() {
        final UUID subjectId = UUID.randomUUID();

        final LedgerEntry e1 = repo.save(entry(subjectId), DEFAULT_TENANT_ID);
        final LedgerEntry e2 = repo.save(entry(subjectId), DEFAULT_TENANT_ID);
        final LedgerEntry e3 = repo.save(entry(subjectId), DEFAULT_TENANT_ID);

        assertThat(List.of(e1.sequenceNumber, e2.sequenceNumber, e3.sequenceNumber))
                .containsExactly(1, 2, 3);
    }

    @Test
    void perSubjectSequencesAreIndependent_inH2StandardMode() {
        final UUID subjectA = UUID.randomUUID();
        final UUID subjectB = UUID.randomUUID();

        final LedgerEntry a1 = repo.save(entry(subjectA), DEFAULT_TENANT_ID);
        final LedgerEntry b1 = repo.save(entry(subjectB), DEFAULT_TENANT_ID);
        final LedgerEntry a2 = repo.save(entry(subjectA), DEFAULT_TENANT_ID);

        assertThat(a1.sequenceNumber).isEqualTo(1);
        assertThat(b1.sequenceNumber).isEqualTo(1);
        assertThat(a2.sequenceNumber).isEqualTo(2);
    }

    private TestEntry entry(final UUID subjectId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "tester";
        return e;
    }
}
