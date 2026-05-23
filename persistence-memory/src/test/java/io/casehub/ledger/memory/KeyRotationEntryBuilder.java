package io.casehub.ledger.memory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import io.casehub.ledger.api.model.KeyRotationReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.KeyRotationEntry;
import io.casehub.platform.api.identity.ActorType;

/**
 * Test factory for {@link KeyRotationEntry} — subclass needed to access protected fields
 * after Quarkus bytecode enhancement.
 *
 * <p>
 * Quarkus bytecode-transforms {@code @Entity} classes and places them in a separate
 * classloader from the test class. Field assignments must occur inside a subclass
 * of the entity to satisfy the protected access restriction.
 */
class KeyRotationEntryBuilder extends KeyRotationEntry {

    static KeyRotationEntry build(String actorId, String prevKey, String newKey,
            KeyRotationReason reason, Instant occurredAt, Instant effectiveSince) {
        KeyRotationEntryBuilder e = new KeyRotationEntryBuilder();
        e.subjectId = UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = occurredAt;
        e.previousKeyRef = prevKey;
        e.newKeyRef = newKey;
        e.reason = reason;
        e.effectiveSince = effectiveSince;
        return e;
    }

    // ── protected-field accessors for test assertions ──────────────────────────
    // These run inside a KeyRotationEntry subclass, so protected access is valid.

    Instant getOccurredAt() { return this.occurredAt; }
    Instant getEffectiveSince() { return this.effectiveSince; }
    KeyRotationReason getReason() { return this.reason; }
    String getPreviousKeyRef() { return this.previousKeyRef; }
    String getActorId() { return this.actorId; }
}
