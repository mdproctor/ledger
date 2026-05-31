package io.casehub.ledger.runtime.service;

/**
 * CDI event fired by {@link KeyRotationService#recordRotation} after a key rotation is persisted.
 *
 * @param actorId        the actor whose key was rotated
 * @param previousKeyRef keyRef of the retired key; {@code null} if unknown
 * @param newKeyRef      keyRef of the replacement key; {@code null} for pure revocation
 */
public record AgentKeyRotatedEvent(String actorId, String previousKeyRef, String newKeyRef) {}
