package io.casehub.ledger.runtime.model;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import io.casehub.ledger.api.model.ErasureReason;

/**
 * A first-class immutable ledger entry recording a GDPR Art.17 erasure event.
 *
 * <p>Opt-in: written by {@code LedgerErasureService} when
 * {@code casehub.ledger.erasure.receipt.enabled=true}. The receipt is part of the
 * Merkle chain — the act of forgetting is itself tamper-evident.
 *
 * <p>{@link #subjectId} is derived deterministically as
 * {@code UUID.nameUUIDFromBytes(erasedActorId.getBytes(UTF_8))}, making the full
 * erasure history for an actor queryable as an ordered ledger sequence — identical
 * to the convention used by {@link KeyRotationEntry} and {@link ActorIdentityBindingEntry}.
 *
 * <p>{@link #erasedActorId} stores the raw identity that was erased. This is intentional:
 * after erasure, the receipt is the only tamper-evident proof that erasure happened for
 * a specific actor. Retaining this proof is a GDPR compliance obligation, not a violation.
 *
 * <p>{@link #entryType} is set to {@link io.casehub.ledger.api.model.LedgerEntryType#EVENT}
 * by {@code LedgerErasureService} before persist.
 */
@NamedQueries({
    @NamedQuery(
        name = "ErasureReceiptLedgerEntry.findByErasedActorId",
        query = "SELECT e FROM ErasureReceiptLedgerEntry e " +
                "WHERE e.erasedActorId = :erasedActorId AND e.tenancyId = :tenancyId " +
                "ORDER BY e.occurredAt ASC")
})
@Entity
@Table(name = "erasure_receipt_entry")
@DiscriminatorValue("ERASURE_RECEIPT")
public class ErasureReceiptLedgerEntry extends LedgerEntry {

    /** The raw actor identity that was erased. Stored before the token→identity mapping is severed. */
    @Column(name = "erased_actor_id", nullable = false)
    public String erasedActorId;

    /** The legal basis or trigger for this erasure event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "erasure_reason", nullable = false)
    public ErasureReason erasureReason;

    /** Count of ledger entries whose {@code actorId} was the severed token. Informational. */
    @Column(name = "affected_entry_count", nullable = false)
    public long affectedEntryCount;

    /** Whether a token→identity mapping actually existed and was severed. */
    @Column(name = "mapping_found", nullable = false)
    public boolean mappingFound;

    @Override
    protected byte[] domainContentBytes() {
        final String content = String.join("|",
            erasedActorId != null ? erasedActorId : "",
            erasureReason != null ? erasureReason.name() : "",
            String.valueOf(affectedEntryCount),
            String.valueOf(mappingFound)
        );
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
