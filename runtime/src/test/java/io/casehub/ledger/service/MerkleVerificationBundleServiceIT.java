package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.compliance.VerificationBundle;
import io.casehub.ledger.runtime.service.MerkleVerificationBundleService;
import io.casehub.ledger.service.supplement.TestEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MerkleVerificationBundleServiceIT {

    @Inject MerkleVerificationBundleService bundleService;
    @Inject LedgerEntryRepository repo;

    @Test
    @Transactional
    void generateBundle_twoSubjects_bundleContainsBoth() {
        final UUID subject1 = UUID.randomUUID();
        final UUID subject2 = UUID.randomUUID();
        final String tenancyId = "bundle-two-" + UUID.randomUUID();

        repo.save(entry(subject1, "actor-1"), tenancyId);
        repo.save(entry(subject2, "actor-2"), tenancyId);

        final VerificationBundle bundle = bundleService.generateBundle(tenancyId);

        assertThat(bundle.tenancyId()).isEqualTo(tenancyId);
        assertThat(bundle.generatedAt()).isNotNull();
        assertThat(bundle.chains()).hasSize(2);
        assertThat(bundle.chains()).allMatch(c -> !c.entries().isEmpty());
        assertThat(bundle.chains()).allMatch(c -> c.storedRoot() != null);
        assertThat(bundle.verificationScript()).contains("def verify_chain");
        assertThat(bundle.verificationInstructions()).contains("python3");
    }

    @Test
    @Transactional
    void generateBundle_emptyTenancy_emptyChains() {
        final String tenancyId = "bundle-empty-" + UUID.randomUUID();

        final VerificationBundle bundle = bundleService.generateBundle(tenancyId);

        assertThat(bundle.chains()).isEmpty();
        assertThat(bundle.verificationScript()).isNotEmpty();
    }

    @Test
    @Transactional
    void generateBundle_chainEntriesMatchDigests() {
        final UUID subject = UUID.randomUUID();
        final String tenancyId = "bundle-digest-" + UUID.randomUUID();

        repo.save(entry(subject, "actor-d1"), tenancyId);
        repo.save(entry(subject, "actor-d2"), tenancyId);

        final VerificationBundle bundle = bundleService.generateBundle(tenancyId);

        assertThat(bundle.chains()).hasSize(1);
        assertThat(bundle.chains().get(0).entries()).hasSize(2);
        assertThat(bundle.chains().get(0).entries()).allMatch(e -> e.digest() != null);
        assertThat(bundle.chains().get(0).entries().get(0).sequenceNumber())
                .isLessThan(bundle.chains().get(0).entries().get(1).sequenceNumber());
    }

    @Test
    @Transactional
    void generateBundle_frontierNodesPresent() {
        final UUID subject = UUID.randomUUID();
        final String tenancyId = "bundle-frontier-" + UUID.randomUUID();

        repo.save(entry(subject, "actor-f1"), tenancyId);

        final VerificationBundle bundle = bundleService.generateBundle(tenancyId);

        assertThat(bundle.chains().get(0).frontier()).isNotEmpty();
        assertThat(bundle.chains().get(0).frontier()).allMatch(f -> f.hash() != null);
    }

    private static TestEntry entry(final UUID subjectId, final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = subjectId;
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "Verifier";
        e.occurredAt = Instant.now();
        return e;
    }
}
