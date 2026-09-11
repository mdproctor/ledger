package io.casehub.ledger.core.model;

public record AgentKeyRotatedEvent(String actorId, String previousKeyRef, String newKeyRef) {}
