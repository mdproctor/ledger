package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Verifies that two tenants sharing a nameUUID-derived {@code subjectId} (the
 * {@link io.casehub.ledger.runtime.model.KeyRotationEntry} case) produce independent Merkle
 * frontiers and that saves by one tenant do not overwrite the other's frontier.
 *
 * <p>
 * Uses {@code KeyRotationService.recordRotation()} because {@code KeyRotationEntry} saves via
 * {@code LedgerEntryRepository.save()} → {@code JpaLedgerEntryRepository.updateMerkleFrontier()}
 * → {@code LedgerMerkleFrontierRepository.replace()}, which is the frontier write path under test.
 */
@QuarkusTest
@TestProfile(MerkleFrontierTenancyIT.MerkleProfile.class)
class MerkleFrontierTenancyIT {

    public static class MerkleProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "merkle-tenancy-test";
        }
    }

    @Inject
    KeyRotationService rotationService;

    @Inject
    LedgerMerkleFrontierRepository frontierRepo;

    // ── Happy path: independent frontiers per tenant ──────────────────────────

    @Test
    @Transactional
    void sameActorId_differentTenants_independentFrontiers() {
        final String actorId = "claude:reviewer@v1-tenancy-test-" + UUID.randomUUID();
        final UUID subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        final String tenantA = "tenant-a-" + UUID.randomUUID();
        final String tenantB = "tenant-b-" + UUID.randomUUID();

        rotationService.recordRotation(actorId, null, "key-a", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);
        rotationService.recordRotation(actorId, null, "key-b", KeyRotationReason.SCHEDULED, Instant.now(), tenantB);

        final List<LedgerMerkleFrontier> frontierA = frontierRepo.findBySubjectId(subjectId, tenantA);
        final List<LedgerMerkleFrontier> frontierB = frontierRepo.findBySubjectId(subjectId, tenantB);

        assertThat(frontierA).isNotEmpty();
        assertThat(frontierB).isNotEmpty();
        assertThat(frontierA.get(0).hash).isNotEqualTo(frontierB.get(0).hash);
    }

    // ── Correctness: tenant A save does not clobber tenant B frontier ─────────

    @Test
    @Transactional
    void tenantA_additionalSave_doesNotOverwriteTenantB_frontier() {
        final String actorId = "claude:reviewer@v1-clobber-test-" + UUID.randomUUID();
        final UUID subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        final String tenantA = "tenant-a-" + UUID.randomUUID();
        final String tenantB = "tenant-b-" + UUID.randomUUID();

        rotationService.recordRotation(actorId, null, "b-key-1", KeyRotationReason.SCHEDULED, Instant.now(), tenantB);
        final List<LedgerMerkleFrontier> frontierBBefore = frontierRepo.findBySubjectId(subjectId, tenantB);
        final String hashBBefore = frontierBBefore.get(0).hash;

        // Tenant A writes twice to the same subjectId — should not affect tenant B's frontier
        rotationService.recordRotation(actorId, null, "a-key-1", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);
        rotationService.recordRotation(actorId, "a-key-1", "a-key-2", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);

        final List<LedgerMerkleFrontier> frontierBAfter = frontierRepo.findBySubjectId(subjectId, tenantB);
        assertThat(frontierBAfter).hasSize(1);
        assertThat(frontierBAfter.get(0).hash).isEqualTo(hashBBefore);
    }

    // ── Correctness: per-tenant sequence counter produces contiguous sequence ──

    @Test
    @Transactional
    void perTenantSequenceNumbers_areContiguousStartingAt1() {
        final String actorId = "claude:reviewer@v1-seq-test-" + UUID.randomUUID();
        final String tenantA = "tenant-a-" + UUID.randomUUID();
        final String tenantB = "tenant-b-" + UUID.randomUUID();

        // Save three entries for tenant A, two for tenant B, all to the same subjectId
        final var a1 = rotationService.recordRotation(actorId, null, "a1", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);
        final var b1 = rotationService.recordRotation(actorId, null, "b1", KeyRotationReason.SCHEDULED, Instant.now(), tenantB);
        final var a2 = rotationService.recordRotation(actorId, "a1", "a2", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);
        final var b2 = rotationService.recordRotation(actorId, "b1", "b2", KeyRotationReason.SCHEDULED, Instant.now(), tenantB);
        final var a3 = rotationService.recordRotation(actorId, "a2", "a3", KeyRotationReason.SCHEDULED, Instant.now(), tenantA);

        // Tenant A: should be 1, 2, 3 — no interleaving with tenant B
        assertThat(a1.sequenceNumber).isEqualTo(1);
        assertThat(a2.sequenceNumber).isEqualTo(2);
        assertThat(a3.sequenceNumber).isEqualTo(3);

        // Tenant B: should be 1, 2 — independent counter
        assertThat(b1.sequenceNumber).isEqualTo(1);
        assertThat(b2.sequenceNumber).isEqualTo(2);
    }
}
