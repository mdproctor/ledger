package io.casehub.ledger.runtime.service.model;

/**
 * Result of an agent signature verification on a {@link io.casehub.ledger.runtime.model.LedgerEntry}.
 */
public enum VerificationResult {

    /** No agent signature is stored on this entry — the actor did not sign. */
    UNSIGNED,

    /** Signature is present and verified against the stored public key and canonical bytes. */
    VALID,

    /** Signature is present but verification failed — possible tampering or key mismatch. */
    INVALID
}
