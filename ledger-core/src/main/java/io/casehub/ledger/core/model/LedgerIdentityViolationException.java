package io.casehub.ledger.core.model;

import io.casehub.platform.api.identity.IdentityBindingStatus;

public class LedgerIdentityViolationException extends RuntimeException {

    public final String actorId;
    public final IdentityBindingStatus status;

    public LedgerIdentityViolationException(final String actorId, final IdentityBindingStatus status) {
        super("Identity binding validation failed for actor " + actorId + ": " + status);
        this.actorId = actorId;
        this.status = status;
    }
}
