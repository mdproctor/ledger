package io.casehub.ledger.service.identity;

import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.identity.ActorIdentityValidationEnricher;
import io.casehub.platform.api.identity.AgentIdentityValidatedEvent;
import io.casehub.platform.api.identity.AgentIdentityViolationEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActorIdentityValidationEnricherTest {

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

    LedgerEntry entry(String actorId, String actorDid, byte[] pubKey) {
        var e = new ConcreteEntry();
        e.actorId = actorId;
        e.actorDid = actorDid;
        e.agentPublicKey = pubKey;
        return e;
    }

    @Test
    void skipsWhenActorDidIsNull() {
        var e = new ConcreteEntry();
        e.actorId = "claude:r@v1";
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isNull();
        verifyNoInteractions(resolver);
    }

    @Test
    void setsDIDUnresolvable() {
        when(resolver.resolve("did:web:x")).thenReturn(Optional.empty());
        var e = entry("claude:r@v1", "did:web:x", null);
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.DID_UNRESOLVABLE);
    }

    @Test
    void setsIdentityMismatch() {
        var doc = new DIDDocument("did:web:x", List.of(), List.of("other:actor@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", null);
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.IDENTITY_MISMATCH);
    }

    @Test
    void setsUnsignedWhenNoPubKey() {
        var doc = new DIDDocument("did:web:x", List.of(), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", null); // null pubKey
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.UNSIGNED);
    }

    @Test
    void setsKeyMismatch() {
        var vm = new VerificationMethod("id", "Ed25519", new byte[]{1, 2, 3});
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", new byte[]{9, 9, 9});
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.KEY_MISMATCH);
    }

    @Test
    void setsValid() {
        byte[] key = {1, 2, 3};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.VALID);
    }

    @Test
    void setsCredentialInvalid() {
        byte[] key = {1};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        when(credValidator.validate("claude:r@v1", "did:web:x"))
            .thenReturn(Optional.of(CredentialValidationResult.INVALID_SIGNATURE));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.CREDENTIAL_INVALID);
    }

    @Test
    void setsCredentialExpired() {
        byte[] key = {1};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        when(credValidator.validate("claude:r@v1", "did:web:x"))
            .thenReturn(Optional.of(CredentialValidationResult.EXPIRED));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        assertThat(e.pendingIdentityStatus).isEqualTo(IdentityBindingStatus.CREDENTIAL_EXPIRED);
    }

    @Test
    void cacheHitSkipsResolutionAndEventFiring() {
        byte[] key = {1};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        enricher.enrich(e); // second call — should use cache
        verify(resolver, times(1)).resolve("did:web:x"); // resolved once
        verify(event, times(1)).fireAsync(any()); // event fired once
    }

    @Test
    void invalidateSingleActorClearsOnlyThatActor() {
        byte[] keyA = {1};
        byte[] keyB = {2};
        var vmA = new VerificationMethod("idA", "Ed25519", keyA);
        var vmB = new VerificationMethod("idB", "Ed25519", keyB);
        var docA = new DIDDocument("did:web:a", List.of(vmA), List.of("actor:a@v1"));
        var docB = new DIDDocument("did:web:b", List.of(vmB), List.of("actor:b@v1"));
        when(resolver.resolve("did:web:a")).thenReturn(Optional.of(docA));
        when(resolver.resolve("did:web:b")).thenReturn(Optional.of(docB));
        // Prime both caches
        var eA = entry("actor:a@v1", "did:web:a", keyA);
        var eB = entry("actor:b@v1", "did:web:b", keyB);
        enricher.enrich(eA);
        enricher.enrich(eB);
        // Invalidate only A
        enricher.invalidate("actor:a@v1");
        enricher.enrich(eA);
        enricher.enrich(eB);
        verify(resolver, times(2)).resolve("did:web:a"); // A was re-resolved
        verify(resolver, times(1)).resolve("did:web:b"); // B was not
    }

    @Test
    void invalidateAllClearsCache() {
        byte[] key = {1};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        enricher.invalidateAll();
        enricher.enrich(e);
        verify(resolver, times(2)).resolve("did:web:x");
    }

    @Test
    void isNonFatal() {
        when(resolver.resolve(any())).thenThrow(new RuntimeException("boom"));
        var e = new ConcreteEntry();
        e.actorId = "a";
        e.actorDid = "did:web:x";
        assertThatCode(() -> enricher.enrich(e)).doesNotThrowAnyException();
    }

    @Test
    void firesValidatedEventOnValid() {
        byte[] key = {1};
        var vm = new VerificationMethod("id", "Ed25519", key);
        var doc = new DIDDocument("did:web:x", List.of(vm), List.of("claude:r@v1"));
        when(resolver.resolve("did:web:x")).thenReturn(Optional.of(doc));
        var e = entry("claude:r@v1", "did:web:x", key);
        enricher.enrich(e);
        verify(event).fireAsync(argThat(ev -> ev instanceof AgentIdentityValidatedEvent));
    }

    @Test
    void firesViolationEventOnNonValid() {
        when(resolver.resolve("did:web:x")).thenReturn(Optional.empty());
        var e = entry("claude:r@v1", "did:web:x", null);
        enricher.enrich(e);
        verify(event).fireAsync(argThat(ev -> ev instanceof AgentIdentityViolationEvent));
    }

    static class ConcreteEntry extends LedgerEntry {}
}
