package io.casehub.ledger.runtime.service.model;

/**
 * Result of an agent signature verification on a {@link io.casehub.ledger.runtime.model.LedgerEntry}.
 */
public enum VerificationResult {

    /** No agent signature is stored on this entry — the actor did not sign. */
    UNSIGNED,

    /** Signature is present and cryptographically verified; key not compromised. */
    VALID,

    /** Signature is present but cryptographic verification failed — possible tampering. */
    INVALID,

    /**
     * Signature is cryptographically VALID but was produced by a key subsequently
     * reported {@link io.casehub.ledger.api.model.KeyRotationReason#COMPROMISED}
     * and the entry's {@code occurredAt} falls within the compromised window.
     * The entry content is intact; the signing actor's trustworthiness is in question.
     */
    SUSPECT
}
