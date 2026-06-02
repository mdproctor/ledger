package io.casehub.ledger.runtime.service;

import io.casehub.platform.api.identity.ActorType;

/** Resolved attestor identity — either from {@code OutcomeRecord} directly or from config defaults. */
record AttestorDefaults(String attestorId, ActorType attestorType) {
}
