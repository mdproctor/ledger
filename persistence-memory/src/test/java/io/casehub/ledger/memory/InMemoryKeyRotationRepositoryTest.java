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
                KeyRotationReason.SCHEDULED, t2, t2));
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "keyRef-B", "keyRef-C",
                KeyRotationReason.SCHEDULED, t1, t1));

        List<KeyRotationEntry> results = rotationRepo.findByActorId(actorId);
        assertThat(results).hasSize(2);
        // ordered by occurredAt ascending: t1 first
        assertThat(occurredAt(results.get(0))).isEqualTo(t1);
        assertThat(occurredAt(results.get(1))).isEqualTo(t2);
    }

    @Test
    void findByActorId_doesNotReturnOtherActors() {
        entryRepo.save(KeyRotationEntryBuilder.build("actor-A", "k1", "k2",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()));
        entryRepo.save(KeyRotationEntryBuilder.build("actor-B", "k1", "k2",
                KeyRotationReason.SCHEDULED, Instant.now(), Instant.now()));

        assertThat(rotationRepo.findByActorId("actor-A")).hasSize(1);
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_filtersCorrectly() {
        String actorId = "claude:reviewer@v1";
        Instant effectiveSince = Instant.parse("2026-03-01T00:00:00Z");

        // COMPROMISED for bad-key — should be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "bad-key", "new-key",
                KeyRotationReason.COMPROMISED, Instant.now(), effectiveSince));
        // COMPROMISED for different key — should NOT be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "other-key", "another",
                KeyRotationReason.COMPROMISED, Instant.now(), effectiveSince));
        // SCHEDULED for bad-key — should NOT be returned
        entryRepo.save(KeyRotationEntryBuilder.build(actorId, "bad-key", "new-key2",
                KeyRotationReason.SCHEDULED, Instant.now(), effectiveSince));

        List<KeyRotationEntry> results =
                rotationRepo.findCompromisedByActorIdAndKeyRef(actorId, "bad-key");
        assertThat(results).hasSize(1);
        assertThat(reason(results.get(0))).isEqualTo(KeyRotationReason.COMPROMISED);
        assertThat(previousKeyRef(results.get(0))).isEqualTo("bad-key");
    }

    @Test
    void findByActorId_returnsEmptyWhenNoEntries() {
        assertThat(rotationRepo.findByActorId("unknown-actor")).isEmpty();
    }

    @Test
    void findCompromisedByActorIdAndKeyRef_returnsEmptyWhenNoneMatch() {
        assertThat(rotationRepo.findCompromisedByActorIdAndKeyRef("actor", "no-key")).isEmpty();
    }

    // ── field readers ─────────────────────────────────────────────────────────
    // Access KeyRotationEntry fields through the KeyRotationEntryBuilder subclass
    // to bypass the protected access restriction from Quarkus bytecode enhancement.

    private static Instant occurredAt(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getOccurredAt();
    }

    private static KeyRotationReason reason(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getReason();
    }

    private static String previousKeyRef(KeyRotationEntry e) {
        return ((KeyRotationEntryBuilder) e).getPreviousKeyRef();
    }
}
