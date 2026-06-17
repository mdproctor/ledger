package io.casehub.ledger.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.quarkus.test.junit.QuarkusTest;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for {@link InMemoryKeyRotationRepository}.
 *
 * <p>
 * Field access on {@link KeyRotationEntry} instances must go through
 * {@link KeyRotationEntryBuilder} accessor methods — Quarkus bytecode enhancement
 * changes {@code @Entity} public fields to {@code protected} in the augmented
 * classloader, so direct field reads from the test class cause
 * {@link IllegalAccessError}. Writes are done inside {@link KeyRotationEntryBuilder}
 * factory methods; reads are done via the builder's accessor methods.
 */
@QuarkusTest
class InMemoryKeyRotationRepositoryTest {

    @Inject
    InMemoryLedgerEntryRepository entryRepo;

    @Inject
    InMemoryKeyRotationRepository rotationRepo;

    @BeforeEach
    void setUp() {
        entryRepo.clear();
    }

    @Test
    void findByActorId_returnsRotationsOrderedByOccurredAt() {
        String actorId = "claude:reviewer@v1";
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

        // save t2 first, then t1 — result must be ordered ascending by occurredAt
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "keyRef-A", "keyRef-B",
                KeyRotationReason.SCHEDULED, t2, t2), DEFAULT_TENANT_ID);
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "keyRef-B", "keyRef-C",
                KeyRotationReason.SCHEDULED, t1, t1), DEFAULT_TENANT_ID);

        List<KeyRotationEntry> results = rotationRepo.findByActorId(actorId, DEFAULT_TENANT_ID);
        assertThat(results).hasSize(2);
        // ordered by occurredAt ascending: t1 first
        assertThat(occurredAt(results.get(0))).isEqualTo(t1);
        assertThat(occurredAt(results.get(1))).isEqualTo(t2);
    }

    @Test
    void findByActorId_doesNotReturnOtherActors() {
        entryRepo.save(KeyRotationEntryBuilder.build("actor-A", "k1", "k2",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()), DEFAULT_TENANT_ID);
        entryRepo.save(KeyRotationEntryBuilder.build("actor-B", "k1", "k2",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()), DEFAULT_TENANT_ID);

        assertThat(rotationRepo.findByActorId("actor-A", DEFAULT_TENANT_ID)).hasSize(1);
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_filtersCorrectly() {
        String actorId = "claude:reviewer@v1";
        Instant effectiveSince = Instant.parse("2026-03-01T00:00:00Z");

        // COMPROMISED for bad-key — should be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "bad-key", "new-key",
                KeyRotationReason.COMPROMISED, Instant.now(), effectiveSince), DEFAULT_TENANT_ID);
        // COMPROMISED for different key — should NOT be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "other-key", "another",
                KeyRotationReason.COMPROMISED, Instant.now(), effectiveSince), DEFAULT_TENANT_ID);
        // SCHEDULED for bad-key — should NOT be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "bad-key", "new-key2",
                KeyRotationReason.SCHEDULED, Instant.now(), effectiveSince), DEFAULT_TENANT_ID);

        List<KeyRotationEntry> results =
                rotationRepo.findCompromisedByActorIdAndKeyRef(actorId, "bad-key");
        assertThat(results).hasSize(1);
        assertThat(reason(results.get(0))).isEqualTo(KeyRotationReason.COMPROMISED);
        assertThat(previousKeyRef(results.get(0))).isEqualTo("bad-key");
    }

    @Test
    void findByActorId_returnsEmptyWhenNoEntries() {
        assertThat(rotationRepo.findByActorId("unknown-actor", DEFAULT_TENANT_ID)).isEmpty();
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_returnsEmptyWhenNoneMatch() {
        assertThat(rotationRepo.findCompromisedByActorIdAndKeyRef("actor", "no-key")).isEmpty();
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_orderedByEffectiveSinceAscending() {
        String actorId = "claude:reviewer@v1";
        String keyRef = "bad-key";
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-06-01T00:00:00Z");

        // save later first, then earlier — result must be ordered ascending by effectiveSince
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, keyRef, "new-key-B",
                KeyRotationReason.COMPROMISED, Instant.now(), later), DEFAULT_TENANT_ID);
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, keyRef, "new-key-A",
                KeyRotationReason.COMPROMISED, Instant.now(), earlier), DEFAULT_TENANT_ID);

        List<KeyRotationEntry> results =
                rotationRepo.findCompromisedByActorIdAndKeyRef(actorId, keyRef);
        assertThat(results).hasSize(2);
        // earlier effectiveSince must come first
        assertThat(effectiveSince(results.get(0))).isEqualTo(earlier);
        assertThat(effectiveSince(results.get(1))).isEqualTo(later);
    }

    @Test
    void findByActorId_isTenantScoped_doesNotReturnOtherTenantEntries() {
        String actorId = "claude:reviewer@v1";
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";

        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "keyRef-A", "keyRef-B",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()), tenantA);
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "keyRef-C", "keyRef-D",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()), tenantB);

        assertThat(rotationRepo.findByActorId(actorId, tenantA)).hasSize(1);
        assertThat(rotationRepo.findByActorId(actorId, tenantB)).hasSize(1);
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_isCrossTenant_returnsAcrossAllTenants() {
        String actorId = "claude:reviewer@v1";
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        Instant effectiveSince = Instant.now().minusSeconds(3600);

        // COMPROMISED reported by tenant A
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "bad-key", "new-key",
                KeyRotationReason.COMPROMISED, Instant.now(), effectiveSince), tenantA);

        // tenant B queries — must see tenant A's compromise report
        List<KeyRotationEntry> results =
                rotationRepo.findCompromisedByActorIdAndKeyRef(actorId, "bad-key");
        assertThat(results).hasSize(1);
    }

    // ── field readers ─────────────────────────────────────────────────────────
    // Access KeyRotationEntry fields through the KeyRotationEntryBuilder subclass
    // to bypass the protected access restriction from Quarkus bytecode enhancement.

    private static Instant occurredAt(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getOccurredAt();
    }

    private static Instant effectiveSince(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getEffectiveSince();
    }

    private static KeyRotationReason reason(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getReason();
    }

    private static String previousKeyRef(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getPreviousKeyRef();
    }
}
