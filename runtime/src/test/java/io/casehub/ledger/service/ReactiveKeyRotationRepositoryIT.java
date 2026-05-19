package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.repository.KeyRotationRepository;
import io.casehub.ledger.runtime.repository.ReactiveKeyRotationRepository;
import io.smallrye.mutiny.Uni;

/**
 * Structural verification of the reactive key rotation SPI.
 *
 * <p>
 * No {@code @QuarkusTest} — Hibernate Reactive requires a Vert.x-based reactive datasource
 * incompatible with the H2 JDBC pool used by the existing test suite.
 *
 * <p>
 * {@link ReactiveKeyRotationRepository} provides the SPI contract. Consumers implement it
 * using their own reactive persistence stack.
 */
class ReactiveKeyRotationRepositoryIT {

    @Test
    void reactiveSpi_allMethodsReturnUni() {
        long uniMethods = Arrays.stream(ReactiveKeyRotationRepository.class.getDeclaredMethods())
                .filter(m -> Uni.class.isAssignableFrom(m.getReturnType()))
                .count();
        assertThat(uniMethods)
                .as("All ReactiveKeyRotationRepository methods must return Uni<T>")
                .isEqualTo(ReactiveKeyRotationRepository.class.getDeclaredMethods().length);
    }

    @Test
    void reactiveSpi_coversAllBlockingSpiMethods() {
        Set<String> blockingNames = Arrays.stream(KeyRotationRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> reactiveNames = Arrays.stream(ReactiveKeyRotationRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(reactiveNames)
                .as("ReactiveKeyRotationRepository must cover all KeyRotationRepository methods")
                .containsAll(blockingNames);
    }
}
