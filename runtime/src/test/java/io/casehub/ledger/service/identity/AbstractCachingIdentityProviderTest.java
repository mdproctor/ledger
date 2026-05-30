package io.casehub.ledger.service.identity;

import io.casehub.ledger.runtime.service.identity.AbstractCachingIdentityProvider;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class AbstractCachingIdentityProviderTest {

    static AbstractCachingIdentityProvider<String> provider(
            Duration ttl, java.util.function.Function<String, Optional<String>> loader) {
        return new AbstractCachingIdentityProvider<>(ttl) {
            @Override protected Optional<String> loadContext(String key) { return loader.apply(key); }
        };
    }

    @Test void cachesOnFirstLoad() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> { calls.incrementAndGet(); return Optional.of("v"); });
        p.get("k"); p.get("k");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void emptyResultIsCached() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> { calls.incrementAndGet(); return Optional.empty(); });
        assertThat(p.get("k")).isEmpty();
        assertThat(p.get("k")).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void throwIsNotCached() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> {
            calls.incrementAndGet();
            throw new RuntimeException("transient");
        });
        assertThatThrownBy(() -> p.get("k")).hasMessage("transient");
        assertThatThrownBy(() -> p.get("k")).hasMessage("transient");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void ttlExpiryTriggersReload() {
        var calls = new AtomicInteger();
        Clock[] clockRef = { Clock.fixed(Instant.EPOCH, ZoneOffset.UTC) };
        var p = new AbstractCachingIdentityProvider<String>(Duration.ofSeconds(10)) {
            @Override protected Optional<String> loadContext(String key) {
                return Optional.of("v" + calls.incrementAndGet());
            }
            @Override protected Instant now() { return clockRef[0].instant(); }
        };
        assertThat(p.get("k")).contains("v1");
        clockRef[0] = Clock.fixed(Instant.EPOCH.plusSeconds(11), ZoneOffset.UTC);
        assertThat(p.get("k")).contains("v2");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void invalidateAllForcesReload() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> { calls.incrementAndGet(); return Optional.of("v"); });
        p.get("k"); p.invalidateAll(); p.get("k");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void invalidateSingleKeyDoesNotEvictOthers() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> { calls.incrementAndGet(); return Optional.of("v"); });
        p.get("a"); p.get("b");
        p.invalidate("a");
        p.get("a"); p.get("b");
        assertThat(calls.get()).isEqualTo(3); // b not reloaded
    }

    @Test void putBypassesLoadContext() {
        var calls = new AtomicInteger();
        var p = provider(Duration.ofMinutes(5), k -> { calls.incrementAndGet(); return Optional.of("loadContext"); });
        p.put("k", Optional.of("direct"));
        assertThat(p.get("k")).contains("direct");
        assertThat(calls.get()).isEqualTo(0); // loadContext never called
    }

    @Test void putValueExpiresAfterTtl() {
        Clock[] clockRef = { Clock.fixed(Instant.EPOCH, ZoneOffset.UTC) };
        var p = new AbstractCachingIdentityProvider<String>(Duration.ofSeconds(10)) {
            @Override protected Optional<String> loadContext(String key) { return Optional.of("reloaded"); }
            @Override protected Instant now() { return clockRef[0].instant(); }
        };
        p.put("k", Optional.of("direct"));
        assertThat(p.get("k")).contains("direct");
        clockRef[0] = Clock.fixed(Instant.EPOCH.plusSeconds(11), ZoneOffset.UTC);
        assertThat(p.get("k")).contains("reloaded"); // expired → loadContext called
    }
}
