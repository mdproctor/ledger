package io.casehub.ledger.core.model;

import java.time.Instant;
import java.util.UUID;

public record AgentSignatureSuspectEvent(
        UUID entryId,
        String actorId,
        String keyRef,
        Instant occurredAt,
        Instant effectiveSince) {}
