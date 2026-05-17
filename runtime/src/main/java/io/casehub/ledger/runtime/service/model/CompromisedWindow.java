package io.casehub.ledger.runtime.service.model;

import java.time.Instant;

/**
 * A time window during which a specific key generation was reported compromised.
 * Entries signed by {@link #keyRef} on or after {@link #effectiveSince} are SUSPECT.
 */
public record CompromisedWindow(String keyRef, Instant effectiveSince) {}
