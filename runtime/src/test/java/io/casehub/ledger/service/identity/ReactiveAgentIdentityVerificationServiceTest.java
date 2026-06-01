package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.service.identity.ReactiveAgentIdentityVerificationService;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@QuarkusTest
class ReactiveAgentIdentityVerificationServiceTest {

    @Inject
    ReactiveAgentIdentityVerificationService service;

    @InjectMock
    DIDResolver resolver;

    private LedgerEntry entry(final String actorDid, final byte[] publicKey) {
        final LedgerEntry e = new ConcreteEntry();
        e.actorId = "claude:tester@v1";
        e.actorType = ActorType.AGENT;
        e.actorRole = "Tester";
        e.actorDid = actorDid;
        e.agentPublicKey = publicKey;
        return e;
    }

    @Test
    void verifyIdentityBindingAsync_nullDid_returnsUnverifiable() {
        final LedgerEntry e = entry(null, new byte[]{1});
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.UNVERIFIABLE);
    }

    @Test
    void verifyIdentityBindingAsync_nullPublicKey_returnsUnsigned() {
        final LedgerEntry e = entry("did:web:example.com", null);
        when(resolver.resolve("did:web:example.com")).thenReturn(Optional.of(
                new DIDDocument("did:web:example.com", List.of(), List.of("claude:tester@v1"))));
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.UNSIGNED);
    }

    @Test
    void verifyIdentityBindingAsync_unresolvedDid_returnsDIDUnresolvable() {
        final LedgerEntry e = entry("did:web:unreachable.example.com", new byte[]{1});
        when(resolver.resolve(anyString())).thenReturn(Optional.empty());
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.DID_UNRESOLVABLE);
    }

    @Test
    void verifyIdentityBindingAsync_actorIdNotInAlsoKnownAs_returnsIdentityMismatch() {
        final byte[] key = new byte[]{1, 2, 3};
        final LedgerEntry e = entry("did:web:example.com", key);
        when(resolver.resolve("did:web:example.com")).thenReturn(Optional.of(
                new DIDDocument("did:web:example.com",
                        List.of(new VerificationMethod("vm1", "Ed25519", key)),
                        List.of("claude:different-actor@v1"))));
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.IDENTITY_MISMATCH);
    }

    @Test
    void verifyIdentityBindingAsync_keyNotInVerificationMethods_returnsKeyMismatch() {
        final byte[] key = new byte[]{1, 2, 3};
        final byte[] differentKey = new byte[]{4, 5, 6};
        final LedgerEntry e = entry("did:web:example.com", key);
        when(resolver.resolve("did:web:example.com")).thenReturn(Optional.of(
                new DIDDocument("did:web:example.com",
                        List.of(new VerificationMethod("vm1", "Ed25519", differentKey)),
                        List.of("claude:tester@v1"))));
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.KEY_MISMATCH);
    }

    @Test
    void verifyIdentityBindingAsync_valid() {
        final byte[] key = new byte[]{1, 2, 3};
        final LedgerEntry e = entry("did:web:example.com", key);
        when(resolver.resolve("did:web:example.com")).thenReturn(Optional.of(
                new DIDDocument("did:web:example.com",
                        List.of(new VerificationMethod("vm1", "Ed25519", key)),
                        List.of("claude:tester@v1"))));
        assertThat(service.verifyIdentityBindingAsync(e)
                .await().atMost(Duration.ofSeconds(5)))
                .isEqualTo(IdentityVerificationResult.VALID);
    }

    static class ConcreteEntry extends LedgerEntry {}
}
