package io.casehub.ledger.api.model;

/**
 * The outcome of validating a Verifiable Credential during agent identity resolution.
 *
 * <p>
 * Returned by {@code AgentCredentialValidator.validate()}. Only {@link #VALID} indicates
 * a credential that may be trusted; all other values cause the binding to be rejected.
 */
public enum CredentialValidationResult {
    /** The credential is structurally sound, its signature checks out, and it is not expired. */
    VALID,
    /** The credential's {@code expirationDate} is in the past. */
    EXPIRED,
    /** The credential's proof signature does not verify against the issuer's key. */
    INVALID_SIGNATURE,
    /** The credential issuer is not in the trusted issuer registry. */
    ISSUER_UNKNOWN,
    /** No credential was found for the requested actor or DID. */
    NOT_FOUND
}
