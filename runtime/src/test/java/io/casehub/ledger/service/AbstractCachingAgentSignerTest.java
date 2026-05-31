package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.AbstractCachingAgentSigner;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.AgentSignature;

class AbstractCachingAgentSignerTest {

    static class TestSigner extends AbstractCachingAgentSigner<String> {
        final AtomicInteger loadCount = new AtomicInteger();
        volatile String contextToReturn = "context";
        volatile boolean throwOnLoad = false;

        @Override
        protected Optional<String> loadContext(final String actorId) {
            loadCount.incrementAndGet();
            if (throwOnLoad) throw new RuntimeException("simulated failure");
            return Optional.ofNullable(contextToReturn);
        }

        @Override
        protected AgentSignature performSign(final String actorId, final String context, final byte[] data) {
            try {
                return AgentSignature.signWith(
                        KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), data);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

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
}
