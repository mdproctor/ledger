package io.casehub.ledger.api.spi.identity;

import io.casehub.ledger.api.model.CredentialValidationResult;

import java.util.Optional;

/**
 * Validates a Verifiable Credential binding claim for an agent actor.
 *
 * <p>
 * Return empty to skip VC validation (e.g. when the deployment does not use VCs).
 * {@link CredentialValidationResult#EXPIRED} must not be cached — credentials may
 * be renewed between calls and a stale {@code EXPIRED} result would block a
 * legitimately refreshed actor.
 *
 * <p>
 * Only {@link CredentialValidationResult#VALID} indicates a credential that
 * may be trusted; all other non-empty results cause the binding to be rejected.
 */
public interface AgentCredentialValidator {

    /**
     * Validates the VC binding for the given actor and DID.
     *
     * @param actorId the ledger actor identifier
     * @param did     the DID URI asserted by the actor
     * @return the validation outcome, or empty to skip VC validation entirely
     */
    Optional<CredentialValidationResult> validate(String actorId, String did);
}
