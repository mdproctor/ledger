package io.casehub.ledger.runtime.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import io.casehub.ledger.api.model.KeyRotationReason;

/**
 * A first-class immutable ledger entry recording a signing key rotation or revocation.
 *
 * <p>
 * {@link #subjectId} is derived deterministically as
 * {@code UUID.nameUUIDFromBytes(actorId.getBytes(UTF_8))}, making the full
 * key lifecycle for an actor queryable as an ordered ledger sequence.
 *
 * <p>
 * {@link #previousKeyRef} is the keyRef of the key being retired.
 * {@link #newKeyRef} is the keyRef of the replacement key (null for pure revocation).
 * {@link #effectiveSince} marks the earliest {@code occurredAt} of entries that are
 * SUSPECT when {@code reason == COMPROMISED}.
 */
@NamedQueries({
    @NamedQuery(
        name = "KeyRotationEntry.findByActorId",
        query = "SELECT e FROM KeyRotationEntry e WHERE e.actorId = :actorId ORDER BY e.occurredAt ASC"),
    @NamedQuery(
        name = "KeyRotationEntry.findCompromisedByActorIdAndKeyRef",
        query = "SELECT e FROM KeyRotationEntry e " +
                "WHERE e.actorId = :actorId " +
                "AND e.previousKeyRef = :keyRef " +
                "AND e.reason = io.casehub.ledger.api.model.KeyRotationReason.COMPROMISED " +
                "ORDER BY e.effectiveSince ASC")
})
@Entity
@Table(name = "key_rotation_entry")
@DiscriminatorValue("KEY_ROTATION")
public class KeyRotationEntry extends LedgerEntry {

    /** keyRef of the key being retired. Null when the previous key is unknown. */
    @Column(name = "previous_key_ref")
    public String previousKeyRef;

    /**
     * keyRef of the replacement key.
     * Null for pure revocation without a replacement (actor suspended).
     */
    @Column(name = "new_key_ref")
    public String newKeyRef;

    /** Why the key was rotated. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    public KeyRotationReason reason;

    /**
     * For {@link KeyRotationReason#COMPROMISED}: entries signed by {@link #previousKeyRef}
     * on or after this timestamp are SUSPECT.
     * For {@link KeyRotationReason#SCHEDULED}: informational only — no entries are flagged.
     */
    @Column(name = "effective_since", nullable = false)
    public Instant effectiveSince;
}
