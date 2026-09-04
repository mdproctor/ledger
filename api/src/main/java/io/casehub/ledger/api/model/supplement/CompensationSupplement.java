package io.casehub.ledger.api.model.supplement;

import java.util.UUID;


/**
 * Supplement marking a ledger entry as a compensation record — the reversal of a
 * previously recorded action as part of a saga compensation sequence.
 *
 * <h2>Saga compensation</h2>
 * <p>
 * When a case enters a COMPENSATING state, each completed step that declared a
 * compensating binding is reversed in topological order. Every compensation action
 * produces a new ledger entry with this supplement attached, linking it to the
 * original entry being reversed.
 *
 * <h2>Fields</h2>
 * <ul>
 * <li>{@link #originalEntryId} — the ledger entry of the original action being
 * reversed. Distinct from {@code causedByEntryId} on the parent entry, which
 * traces the causal chain (the immediate trigger). In simple cases both point
 * to the same entry; in engine-driven compensation the causal chain goes through
 * intermediate entries while this field skips directly to the original.</li>
 * <li>{@link #compensationReason} — human-readable reason for the reversal.</li>
 * <li>{@link #regulatoryBasis} — regulatory article requiring the reversal
 * (e.g. {@code "GDPR Art.17"}, {@code "EU AI Act Art.12"}).</li>
 * <li>{@link #compensationMode} — {@code "automated"} (engine-driven saga) or
 * {@code "human-driven"} (operator action via REST API).</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * JpaCompensationSupplement cs = new JpaCompensationSupplement();
 * cs.originalEntryId = originalActionEntry.id;
 * cs.compensationReason = "Clinical trial withdrawn";
 * cs.regulatoryBasis = "GDPR Art.17";
 * cs.compensationMode = "human-driven";
 * entry.attach(cs);
 * }</pre>
 */
public abstract class CompensationSupplement extends LedgerSupplement {

    /** The ledger entry ID of the original action being compensated. */
    public UUID originalEntryId;

    /** Human-readable reason for compensation. */
    public String compensationReason;

    /** Regulatory basis for compensation (e.g. {@code "GDPR Art.17"}). */
    public String regulatoryBasis;

    /** Whether this is automated or human-driven compensation. */
    public String compensationMode;
}
