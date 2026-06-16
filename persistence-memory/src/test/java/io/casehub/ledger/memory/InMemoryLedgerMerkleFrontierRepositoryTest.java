package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;

class InMemoryLedgerMerkleFrontierRepositoryTest {

    private InMemoryLedgerMerkleFrontierRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryLedgerMerkleFrontierRepository();
    }

    @Test
    void findBySubjectId_returnsEmptyWhenNoneStored() {
        assertThat(repo.findBySubjectId(UUID.randomUUID(), DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void replace_storesAndRetrievesFrontier() {
        UUID subjectId = UUID.randomUUID();
        LedgerMerkleFrontier node = frontier(subjectId, 0, "abc");

        repo.replace(subjectId, List.of(node), DEFAULT_TENANT_ID);

        List<LedgerMerkleFrontier> result = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).hash).isEqualTo("abc");
    }

    @Test
    void replace_overwritesPreviousFrontier() {
        UUID subjectId = UUID.randomUUID();
        repo.replace(subjectId, List.of(frontier(subjectId, 0, "first")), DEFAULT_TENANT_ID);
        repo.replace(subjectId, List.of(frontier(subjectId, 0, "second")), DEFAULT_TENANT_ID);

        assertThat(repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID)).hasSize(1);
        assertThat(repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID).get(0).hash).isEqualTo("second");
    }

    @Test
    void findBySubjectId_returnsDefensiveCopy() {
        UUID subjectId = UUID.randomUUID();
        repo.replace(subjectId, List.of(frontier(subjectId, 0, "abc")), DEFAULT_TENANT_ID);

        List<LedgerMerkleFrontier> result = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        result.clear(); // mutate returned list

        assertThat(repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID)).hasSize(1); // original unchanged
    }

    @Test
    void clear_removesAllFrontiers() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        repo.replace(s1, List.of(frontier(s1, 0, "a")), DEFAULT_TENANT_ID);
        repo.replace(s2, List.of(frontier(s2, 0, "b")), DEFAULT_TENANT_ID);

        repo.clear();

        assertThat(repo.findBySubjectId(s1, DEFAULT_TENANT_ID)).isEmpty();
        assertThat(repo.findBySubjectId(s2, DEFAULT_TENANT_ID)).isEmpty();
    }

    // ── Tenancy isolation ─────────────────────────────────────────────────────

    @Test
    void twoTenantsWithSameSubjectId_haveIndependentFrontiers() {
        final UUID sharedSubjectId = UUID.randomUUID();
        final String tenantA = "tenant-a";
        final String tenantB = "tenant-b";

        repo.replace(sharedSubjectId, List.of(frontier(sharedSubjectId, 0, "hash-from-a")), tenantA);
        repo.replace(sharedSubjectId, List.of(frontier(sharedSubjectId, 0, "hash-from-b")), tenantB);

        final List<LedgerMerkleFrontier> frontierA = repo.findBySubjectId(sharedSubjectId, tenantA);
        final List<LedgerMerkleFrontier> frontierB = repo.findBySubjectId(sharedSubjectId, tenantB);

        assertThat(frontierA).hasSize(1);
        assertThat(frontierB).hasSize(1);
        assertThat(frontierA.get(0).hash).isEqualTo("hash-from-a");
        assertThat(frontierB.get(0).hash).isEqualTo("hash-from-b");
    }

    @Test
    void tenantA_replace_doesNotOverwriteTenantB_frontier() {
        final UUID sharedSubjectId = UUID.randomUUID();
        final String tenantA = "tenant-a";
        final String tenantB = "tenant-b";

        repo.replace(sharedSubjectId, List.of(frontier(sharedSubjectId, 0, "b-original")), tenantB);
        repo.replace(sharedSubjectId, List.of(frontier(sharedSubjectId, 0, "a-write")), tenantA);
        repo.replace(sharedSubjectId, List.of(frontier(sharedSubjectId, 0, "a-second-write")), tenantA);

        assertThat(repo.findBySubjectId(sharedSubjectId, tenantB).get(0).hash)
                .isEqualTo("b-original");
    }

    private LedgerMerkleFrontier frontier(UUID subjectId, int level, String hash) {
        LedgerMerkleFrontier f = new LedgerMerkleFrontier();
        f.id = UUID.randomUUID();
        f.subjectId = subjectId;
        f.level = level;
        f.hash = hash;
        return f;
    }
}
