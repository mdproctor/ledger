package io.casehub.ledger.memory;

import java.time.Instant;
import java.util.UUID;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.platform.api.identity.ActorType;

/**
 * Concrete non-entity subclass of {@link LedgerEntry} for in-memory persistence tests.
 * No JPA annotations — the in-memory store does not use Hibernate.
 *
 * <p>
 * All field assignments and reads happen inside this class (a subclass of
 * {@link LedgerEntry}) to satisfy the {@code protected} access restriction that Quarkus
 * bytecode enhancement applies to {@code @Entity} fields during augmentation. Accessing
 * {@code LedgerEntry} fields directly from a non-subclass test context causes
 * {@link IllegalAccessError}.
 */
public class MemoryTestEntry extends LedgerEntry {

    /** Create a minimal test entry with the given subject and type. */
    public static MemoryTestEntry of(final UUID subjectId, final LedgerEntryType type) {
        final MemoryTestEntry e = new MemoryTestEntry();
        e.subjectId = subjectId;
        e.entryType = type;
        e.actorId = "test-actor";
        e.actorType = ActorType.AGENT;
        e.actorRole = "TestRole";
        return e;
    }

    // ── fluent mutators ───────────────────────────────────────────────────────

    /** Override actorId. */
    public MemoryTestEntry withActorId(final String newActorId) {
        this.actorId = newActorId;
        return this;
    }

    /** Override actorRole. */
    public MemoryTestEntry withActorRole(final String newRole) {
        this.actorRole = newRole;
        return this;
    }

    /** Override actorType. */
    public MemoryTestEntry withActorType(final ActorType newType) {
        this.actorType = newType;
        return this;
    }

    /** Override occurredAt. */
    public MemoryTestEntry withOccurredAt(final Instant time) {
        this.occurredAt = time;
        return this;
    }

    /** Set causedByEntryId. */
    public MemoryTestEntry withCausedBy(final UUID entryId) {
        this.causedByEntryId = entryId;
        return this;
    }

    /** Clear id (for testing id-assignment). */
    public MemoryTestEntry withNullId() {
        this.id = null;
        return this;
    }

    /** Clear occurredAt (for testing occurredAt-assignment). */
    public MemoryTestEntry withNullOccurredAt() {
        this.occurredAt = null;
        return this;
    }

    // ── protected-field accessors for test assertions ─────────────────────────
    // These run inside a LedgerEntry subclass, so protected access is valid.

    public UUID getId() { return this.id; }
    public UUID getSubjectId() { return this.subjectId; }
    public int getSequenceNumber() { return this.sequenceNumber; }
    public LedgerEntryType getEntryType() { return this.entryType; }
    public String getActorId() { return this.actorId; }
    public Instant getOccurredAt() { return this.occurredAt; }
    public UUID getCausedByEntryId() { return this.causedByEntryId; }
}
