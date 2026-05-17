package io.casehub.ledger.api.model;

/**
 * Why an agent signing key was rotated.
 *
 * <p>
 * Per NIST SP 800-57, key rotation (planned) and key revocation (compromise)
 * are distinct lifecycle events requiring distinct records and response procedures.
 */
public enum KeyRotationReason {

    /**
     * Planned rotation due to cryptoperiod policy — no compromise assumed.
     * Entries signed by the previous key remain VALID.
     */
    SCHEDULED,

    /**
     * Emergency rotation because the key was leaked or the actor was misbehaving.
     * Entries signed by the previous key on or after {@code effectiveSince} are SUSPECT.
     */
    COMPROMISED
}
