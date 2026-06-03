package io.casehub.ledger.runtime.service.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.event.Event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;

/**
 * Tests the package-private {@link ActorIdentityValidationEnricher#onKeyRotated} observer
 * directly. Kept in the same package to access the package-private method.
 */
class ActorIdentityValidationEnricherObserverTest {

    DIDResolver resolver = mock(DIDResolver.class);
    AgentCredentialValidator credValidator = mock(AgentCredentialValidator.class);
    @SuppressWarnings("unchecked")
    Event<Object> event = mock(Event.class, RETURNS_DEEP_STUBS);
    ActorIdentityValidationEnricher enricher;

    @BeforeEach
    void setUp() {
        when(credValidator.validate(any(), any())).thenReturn(Optional.empty());
        enricher = new ActorIdentityValidationEnricher(resolver, credValidator, event,
                Duration.ofMinutes(5));
    }

    @Test
    void onKeyRotated_evictsActorFromStatusCache() {
        final byte[] key = {1};
        final var vm = new VerificationMethod("id", "Ed25519", key);
        final var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        final var entry = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(entry); // prime cache — resolver called once

        enricher.onKeyRotated(new AgentKeyRotatedEvent("claude:r@v1", null, null));

        enricher.enrich(entry); // cache miss after eviction — resolver called again
        verify(resolver, times(2)).resolve("did:web:x");
    }

    private LedgerEntry entry(final String actorId, final String actorDid, final byte[] pubKey) {
        final var e = new ConcreteEntry();
        e.actorId = actorId;
        e.actorDid = actorDid;
        e.agentPublicKey = pubKey;
        return e;
    }

    static class ConcreteEntry extends LedgerEntry {}
}
