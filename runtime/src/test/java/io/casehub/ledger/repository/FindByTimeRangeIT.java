package io.casehub.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FindByTimeRangeIT {

    @Inject LedgerEntryRepository repo;

    @Test
    @Transactional
    void findByTimeRange_returnsEntriesInRange() {
        final String actorA = "time-range-a-" + UUID.randomUUID();
        final String actorB = "time-range-b-" + UUID.randomUUID();
        final UUID subject1 = UUID.randomUUID();
        final UUID subject2 = UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(subject1, actorA), DEFAULT_TENANT_ID);
        repo.save(entry(subject2, actorB), DEFAULT_TENANT_ID);

        final List<LedgerEntry> result = repo.findByTimeRange(from, to, DEFAULT_TENANT_ID);

        assertThat(result).anyMatch(e -> actorA.equals(e.actorId));
        assertThat(result).anyMatch(e -> actorB.equals(e.actorId));
    }

    @Test
    @Transactional
    void findByTimeRange_excludesOutsideRange() {
        final String actorId = "time-range-past-" + UUID.randomUUID();
        final UUID subject = UUID.randomUUID();
        final Instant pastFrom = Instant.now().minus(2, ChronoUnit.DAYS);
        final Instant pastTo = Instant.now().minus(1, ChronoUnit.DAYS);

        repo.save(entry(subject, actorId), DEFAULT_TENANT_ID);

        final List<LedgerEntry> result = repo.findByTimeRange(pastFrom, pastTo, DEFAULT_TENANT_ID);

        assertThat(result).noneMatch(e -> actorId.equals(e.actorId));
    }

    @Test
    @Transactional
    void findByTimeRange_isolatesByTenancy() {
        final UUID subject = UUID.randomUUID();
        final String actorA = "time-range-tenA-" + UUID.randomUUID();
        final String actorB = "time-range-tenB-" + UUID.randomUUID();
        final String tenantA = "tenant-a-" + UUID.randomUUID();
        final String tenantB = "tenant-b-" + UUID.randomUUID();
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        repo.save(entry(subject, actorA), tenantA);
        repo.save(entry(subject, actorB), tenantB);

        final List<LedgerEntry> resultA = repo.findByTimeRange(from, to, tenantA);

        assertThat(resultA).allMatch(e -> actorA.equals(e.actorId));
    }

    @Test
    @Transactional
    void findDistinctSubjectIds_returnsUniqueSubjects() {
        final UUID subject1 = UUID.randomUUID();
        final UUID subject2 = UUID.randomUUID();
        final String tenancyId = "distinct-" + UUID.randomUUID();

        repo.save(entry(subject1, "actor-d1"), tenancyId);
        repo.save(entry(subject1, "actor-d2"), tenancyId);
        repo.save(entry(subject2, "actor-d3"), tenancyId);

        final List<UUID> subjects = repo.findDistinctSubjectIds(tenancyId);

        assertThat(subjects).containsExactlyInAnyOrder(subject1, subject2);
    }

    private static TestEntry entry(final UUID subjectId, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "Reporter";
        e.occurredAt = Instant.now();
        return e;
    }
}
