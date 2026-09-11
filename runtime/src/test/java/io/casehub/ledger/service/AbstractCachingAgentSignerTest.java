package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.model.AgentKeyRotatedEvent;
import io.casehub.ledger.core.signing.AbstractCachingAgentSigner;
import io.casehub.ledger.core.signing.AgentKeyMaterial;
import io.casehub.ledger.core.signing.AgentSignature;

class AbstractCachingAgentSignerTest {

    static class TestSigner extends AbstractCachingAgentSigner<TestContext> {
        final AtomicInteger loadCount = new AtomicInteger();
        final AtomicInteger performSignCount = new AtomicInteger();
        volatile TestContext contextToReturn;
        volatile boolean throwOnLoad = false;

        TestSigner() {
            try {
                final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                this.contextToReturn = new TestContext(kp);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected Optional<TestContext> loadContext(final String actorId) {
            loadCount.incrementAndGet();
            if (throwOnLoad) throw new RuntimeException("simulated failure");
            return Optional.ofNullable(contextToReturn);
        }

        @Override
        protected AgentSignature performSign(final String actorId, final TestContext context, final byte[] data) {
            performSignCount.incrementAndGet();
            return AgentSignature.signWith(context.keyPair(), data);
        }

        @Override
        protected PublicKey contextPublicKey(final TestContext context) {
            return context.keyPair().getPublic();
        }
    }

    record TestContext(KeyPair keyPair) {}

    @Test
    void cachesContextAfterFirstLoad() {
        final TestSigner signer = new TestSigner();
        signer.sign("actor1", new byte[]{1});
        signer.sign("actor1", new byte[]{2});
        assertThat(signer.loadCount.get()).isEqualTo(1);
    }

    @Test
    void returnsEmptyForUnconfiguredActor_andCachesAbsence() {
        final TestSigner signer = new TestSigner();
        signer.contextToReturn = null;
        assertThat(signer.sign("unknown", new byte[]{1})).isEmpty();
        assertThat(signer.sign("unknown", new byte[]{1})).isEmpty();
        assertThat(signer.loadCount.get()).isEqualTo(1);
    }

    @Test
    void transientError_notCached_retriesOnNextCall() {
        final TestSigner signer = new TestSigner();
        signer.throwOnLoad = true;
        assertThatThrownBy(() -> signer.sign("actor1", new byte[]{1}))
                .isInstanceOf(RuntimeException.class).hasMessage("simulated failure");

        signer.throwOnLoad = false;
        final Optional<AgentSignature> result = signer.sign("actor1", new byte[]{1});
        assertThat(result).isPresent();
        assertThat(signer.loadCount.get()).isEqualTo(2);
    }

    @Test
    void invalidateAll_forcesReloadOnNextSign() {
        final TestSigner signer = new TestSigner();
        signer.sign("actor1", new byte[]{1});
        signer.invalidateAll();
        signer.sign("actor1", new byte[]{1});
        assertThat(signer.loadCount.get()).isEqualTo(2);
    }

    @Test
    void invalidate_evictsOnlyTargetActor() {
        final TestSigner signer = new TestSigner();
        signer.sign("actor1", new byte[]{1});
        signer.sign("actor2", new byte[]{1});
        signer.invalidate("actor1");
        signer.sign("actor1", new byte[]{1});
        signer.sign("actor2", new byte[]{1});
        assertThat(signer.loadCount.get()).isEqualTo(3);
    }

    @Test
    void returnsPresent_whenContextPresent() {
        final TestSigner signer = new TestSigner();
        assertThat(signer.sign("actor1", new byte[]{1})).isPresent();
    }

    @Test
    void onKeyRotated_invalidatesOnlyTargetActor() {
        final TestSigner signer = new TestSigner();
        signer.sign("actor1", new byte[]{1});
        signer.sign("actor2", new byte[]{1});
        assertThat(signer.loadCount.get()).isEqualTo(2);

        signer.onKeyRotated(new AgentKeyRotatedEvent("actor1", "oldRef", "newRef"));

        signer.sign("actor1", new byte[]{1}); // cache was evicted — reloads
        signer.sign("actor2", new byte[]{1}); // cache intact — no reload
        assertThat(signer.loadCount.get()).isEqualTo(3);
    }

    @Test
    void keyMaterial_returnsKeyWithoutCallingPerformSign() {
        final TestSigner signer = new TestSigner();

        final Optional<AgentKeyMaterial> result = signer.keyMaterial("actor1");

        assertThat(result).isPresent();
        assertThat(signer.loadCount.get()).isEqualTo(1);
        assertThat(signer.performSignCount.get()).isEqualTo(0);
    }

    @Test
    void keyMaterial_returnsEmpty_whenContextAbsent() {
        final TestSigner signer = new TestSigner();
        signer.contextToReturn = null;

        final Optional<AgentKeyMaterial> result = signer.keyMaterial("actor1");

        assertThat(result).isEmpty();
    }

    @Test
    void keyMaterial_usesResolveContext_cacheHit() {
        final TestSigner signer = new TestSigner();
        signer.sign("actor1", new byte[]{1}); // populate cache

        signer.keyMaterial("actor1");

        assertThat(signer.loadCount.get()).isEqualTo(1); // cache hit, no second load
    }

    @Test
    void keyMaterial_keyRefMatchesSignature() {
        final TestSigner signer = new TestSigner();

        final Optional<AgentKeyMaterial> km = signer.keyMaterial("actor1");
        final Optional<AgentSignature> sig = signer.sign("actor1", new byte[]{1, 2, 3});

        assertThat(km).isPresent();
        assertThat(sig).isPresent();
        assertThat(km.get().keyRef()).isEqualTo(sig.get().keyRef());
        assertThat(km.get().publicKey()).isEqualTo(sig.get().publicKey());
    }
}
