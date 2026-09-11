package io.casehub.ledger.core.model;

import java.time.Instant;

public record CompromisedWindow(String keyRef, Instant effectiveSince) {}
