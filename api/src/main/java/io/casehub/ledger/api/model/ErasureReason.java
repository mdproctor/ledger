package io.casehub.ledger.api.model;

/**
 * Why a GDPR Art.17 erasure was performed.
 *
 * <p>Stored on {@link io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry}
 * to make the legal basis for each erasure event queryable and auditable.
 */
public enum ErasureReason {

    /** Subject exercised the Art.17 right-to-erasure. */
    GDPR_ART_17_REQUEST,

    /** Automated retention policy expired the actor's data window. */
    RETENTION_EXPIRED,

    /** Account deletion triggered erasure of all associated ledger identity mappings. */
    ACCOUNT_DELETION
}
