package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.identity.ActorDIDEnricher;

class ActorDIDEnricherTest {

    ActorDIDProvider provider = mock(ActorDIDProvider.class);
    ActorDIDEnricher enricher = new ActorDIDEnricher(provider);

    @Test
    void setsActorDidWhenProviderReturnsValue() {
        when(provider.didFor("claude:reviewer@v1")).thenReturn(Optional.of("did:web:example.com"));
        var entry = new ConcreteEntry();
        entry.actorId = "claude:reviewer@v1";
        enricher.enrich(entry);
        assertThat(entry.actorDid).isEqualTo("did:web:example.com");
    }

    @Test
    void skipsWhenActorIdIsNull() {
        var entry = new ConcreteEntry();
        enricher.enrich(entry);
        verifyNoInteractions(provider);
        assertThat(entry.actorDid).isNull();
    }

    @Test
    void skipsWhenActorDidAlreadySet() {
        var entry = new ConcreteEntry();
        entry.actorId = "claude:reviewer@v1";
        entry.actorDid = "did:web:already.set";
        enricher.enrich(entry);
        verifyNoInteractions(provider);
        assertThat(entry.actorDid).isEqualTo("did:web:already.set");
    }

    @Test
    void leavesActorDidNullWhenProviderReturnsEmpty() {
        when(provider.didFor(any())).thenReturn(Optional.empty());
        var entry = new ConcreteEntry();
        entry.actorId = "claude:reviewer@v1";
        enricher.enrich(entry);
        assertThat(entry.actorDid).isNull();
    }

    @Test
    void isNonFatalWhenProviderThrows() {
        when(provider.didFor(any())).thenThrow(new RuntimeException("network error"));
        var entry = new ConcreteEntry();
        entry.actorId = "claude:reviewer@v1";
        assertThatCode(() -> enricher.enrich(entry)).doesNotThrowAnyException();
        assertThat(entry.actorDid).isNull();
    }

    static class ConcreteEntry extends LedgerEntry {}
}
