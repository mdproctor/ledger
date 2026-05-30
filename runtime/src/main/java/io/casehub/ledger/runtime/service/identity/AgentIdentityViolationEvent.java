package io.casehub.ledger.runtime.service.identity;

import io.casehub.ledger.api.model.IdentityBindingStatus;

/** Fired async when actorId→DID binding validation returns a non-VALID result. */
public record AgentIdentityViolationEvent(
        String actorId,
        String actorDid,
        IdentityBindingStatus status) {}
