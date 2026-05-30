package io.casehub.ledger.service.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.IdentityVerificationResult;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.identity.DIDDocument;
import io.casehub.ledger.api.spi.identity.VerificationMethod;
import io.casehub.ledger.runtime.service.identity.AgentIdentityVerificationService;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link AgentIdentityVerificationService} in a Quarkus container.
 *
 * <p>Verifies the read-path DID identity verification with a live CDI graph and
 * injected {@link InjectableTestDIDResolver} replacing the default no-op resolver.
 */
@QuarkusTest
class AgentIdentityVerificationServiceIT {

    @Inject AgentIdentityVerificationService svc;
    @Inject InjectableTestDIDResolver testResolver;

    @BeforeEach
    void setUp() {
        testResolver.clear();
    }

    @Test
    void validBindingReturnsValid() {
        final byte[] key = {1, 2, 3};
        final var vm = new VerificationMethod("did:web:example.com#key-1", "Ed25519", key);
        testResolver.register("did:web:example.com",
            new DIDDocument("did:web:example.com", List.of(vm), List.of("claude:verify@v1")));

        final var entry = buildEntry("claude:verify@v1", "did:web:example.com", key);

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.VALID);
    }

    @Test
    void unverifiableWhenNoActorDid() {
        final var entry = buildEntry("claude:verify@v1", null, null);

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.UNVERIFIABLE);
    }

    @Test
    void unsignedWhenNoPublicKey() {
        final var vm = new VerificationMethod("did:web:example.com#key-1", "Ed25519", new byte[]{1});
        testResolver.register("did:web:example.com",
            new DIDDocument("did:web:example.com", List.of(vm), List.of("claude:verify@v1")));

        final var entry = buildEntry("claude:verify@v1", "did:web:example.com", null);

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.UNSIGNED);
    }

    @Test
    void didUnresolvableWhenResolverReturnsEmpty() {
        // No DID registered — InjectableTestDIDResolver returns empty
        final var entry = buildEntry("claude:verify@v1", "did:web:unknown.com", new byte[]{1});

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.DID_UNRESOLVABLE);
    }

    @Test
    void identityMismatchWhenActorIdNotInAlsoKnownAs() {
        final byte[] key = {1, 2, 3};
        final var vm = new VerificationMethod("did:web:example.com#key-1", "Ed25519", key);
        testResolver.register("did:web:example.com",
            new DIDDocument("did:web:example.com", List.of(vm), List.of("other:actor@v1")));

        final var entry = buildEntry("claude:verify@v1", "did:web:example.com", key);

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.IDENTITY_MISMATCH);
    }

    @Test
    void keyMismatchWhenPubKeyNotInVerificationMethods() {
        final var vm = new VerificationMethod("did:web:example.com#key-1", "Ed25519", new byte[]{1, 2, 3});
        testResolver.register("did:web:example.com",
            new DIDDocument("did:web:example.com", List.of(vm), List.of("claude:verify@v1")));

        final var entry = buildEntry("claude:verify@v1", "did:web:example.com", new byte[]{9, 9, 9});

        assertThat(svc.verifyIdentityBinding(entry)).isEqualTo(IdentityVerificationResult.KEY_MISMATCH);
    }

    private static TestEntry buildEntry(final String actorId, final String actorDid, final byte[] pubKey) {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.actorRole = "verification-it";
        e.actorDid = actorDid;
        e.agentPublicKey = pubKey;
        return e;
    }
}
