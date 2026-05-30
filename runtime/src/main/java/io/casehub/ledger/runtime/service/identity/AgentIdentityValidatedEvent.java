package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.model.CredentialValidationResult;
import io.casehub.ledger.api.model.IdentityBindingStatus;

/** Fired async when actorId→DID binding validation succeeds (VALID result). */
public record AgentIdentityValidatedEvent(
        String actorId,
        String actorDid,
        IdentityBindingStatus status,
        boolean alsoKnownAsVerified,
        boolean keyMatchVerified,
        String verifiedKeyRef,
        CredentialValidationResult credentialResult,
        String didMethod) {}
