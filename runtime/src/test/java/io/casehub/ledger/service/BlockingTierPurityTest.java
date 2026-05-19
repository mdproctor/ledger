package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.casehub.ledger.runtime.repository.ReactiveKeyRotationRepository;
import io.casehub.ledger.runtime.service.KeyRotationService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.smallrye.mutiny.Uni;

/**
 * Structural verification that blocking-tier service beans contain no reactive
 * ({@code Uni<T>}-returning) methods.
 *
 * <p>
 * Enforces the reactive/blocking tier separation defined by PP-20260519-f2e160.
 * JDBC-only consumers depend on this guarantee — if a blocking-tier bean injects
 * a reactive SPI, the build fails. This test catches violations before deployment.
 */
class BlockingTierPurityTest {

    @Test
    void ledgerVerificationService_hasNoUniMethods() {
        final List<String> uniMethods = uniMethodNames(LedgerVerificationService.class);
        assertThat(uniMethods)
                .as("LedgerVerificationService must contain no Uni<T>-returning methods " +
                        "(reactive variants belong in ReactiveLedgerVerificationService)")
                .isEmpty();
    }

    @Test
    void keyRotationService_hasNoUniMethods() {
        final List<String> uniMethods = uniMethodNames(KeyRotationService.class);
        assertThat(uniMethods)
                .as("KeyRotationService must contain no Uni<T>-returning methods " +
                        "(reactive variants belong in ReactiveKeyRotationService)")
                .isEmpty();
    }

    @Test
    void ledgerVerificationService_doesNotInjectReactiveSpi() {
        final List<String> reactiveFields = reactiveFieldNames(LedgerVerificationService.class);
        assertThat(reactiveFields)
                .as("LedgerVerificationService must not inject reactive SPI types " +
                        "(reactive dependencies belong in ReactiveLedgerVerificationService)")
                .isEmpty();
    }

    @Test
    void keyRotationService_doesNotInjectReactiveSpi() {
        final List<String> reactiveFields = reactiveFieldNames(KeyRotationService.class);
        assertThat(reactiveFields)
                .as("KeyRotationService must not inject reactive SPI types " +
                        "(reactive dependencies belong in ReactiveKeyRotationService)")
                .isEmpty();
    }

    private static List<String> uniMethodNames(final Class<?> cls) {
        return Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> Uni.class.isAssignableFrom(m.getReturnType()))
                .map(Method::getName)
                .toList();
    }

    private static List<String> reactiveFieldNames(final Class<?> cls) {
        return Arrays.stream(cls.getDeclaredFields())
                .filter(f -> ReactiveLedgerEntryRepository.class.isAssignableFrom(f.getType())
                        || ReactiveKeyRotationRepository.class.isAssignableFrom(f.getType()))
                .map(Field::getName)
                .toList();
    }
}
