package io.casehub.ledger.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.privacy.InternalActorIdentityProvider;
import io.casehub.ledger.runtime.privacy.LedgerPrivacyProducer;
import io.casehub.ledger.runtime.privacy.PassThroughActorIdentityProvider;

/**
 * Unit tests for {@link LedgerPrivacyProducer}.
 *
 * <p>
 * Verifies that the producer never accesses the {@link EntityManager} when
 * tokenisation is disabled — the critical invariant that allows the producer
 * to operate in datasource-free deployments (e.g. apps using casehub-qhorus
 * without a ledger JPA datasource).
 */
@ExtendWith(MockitoExtension.class)
class LedgerPrivacyProducerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    LedgerConfig config;

    @SuppressWarnings("unchecked")
    @Mock
    Instance<EntityManager> emInstance;

    @Test
    void actorIdentityProvider_tokenisationDisabled_returnsPassThrough_withoutAccessingEntityManager() {
        when(config.identity().tokenisation().enabled()).thenReturn(false);

        final ActorIdentityProvider result = producer().actorIdentityProvider();

        assertThat(result).isInstanceOf(PassThroughActorIdentityProvider.class);
        verify(emInstance, never()).get();
    }

    @Test
    void actorIdentityProvider_tokenisationEnabled_returnsInternal_andAccessesEntityManager() {
        when(config.identity().tokenisation().enabled()).thenReturn(true);
        when(emInstance.get()).thenReturn(mock(EntityManager.class));

        final ActorIdentityProvider result = producer().actorIdentityProvider();

        assertThat(result).isInstanceOf(InternalActorIdentityProvider.class);
        verify(emInstance).get();
    }

    private LedgerPrivacyProducer producer() {
        final LedgerPrivacyProducer p = new LedgerPrivacyProducer();
        set(p, "config", config);
        set(p, "emInstance", emInstance);
        return p;
    }

    private static void set(final Object target, final String name, final Object value) {
        try {
            final Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
