package io.casehub.ledger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.smallrye.mutiny.Uni;

import io.casehub.ledger.runtime.service.KeyRotationService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural contract: every public, non-static method in a blocking {@code *Service}
 * bean must have a reactive {@code nameAsync} counterpart in the paired
 * {@code Reactive*Service} with identical parameter types, and vice versa.
 *
 * <p>Enforces the parity rule from protocol PP-20260519-39a9a5
 * (reactive-service-build-gating). No {@code @QuarkusTest} — plain bytecode scan,
 * runs without a CDI container.
 */
class BlockingReactiveParityTest {

    private static final JavaClasses SERVICE_CLASSES = new ClassFileImporter()
            .importPackages(KeyRotationService.class.getPackageName());

    @Test
    void reactiveAndBlockingServices_mustHaveBidirectionalMethodParity() {
        assertThat(reactiveServiceCount())
                .as("expected at least 2 Reactive*Service classes in scope — "
                        + "if the package moved, update the ClassFileImporter")
                .isGreaterThanOrEqualTo(2);

        classes()
                .that().haveSimpleNameStartingWith("Reactive")
                .and().haveSimpleNameEndingWith("Service")
                .should(haveBidirectionalMethodParity(SERVICE_CLASSES))
                .check(SERVICE_CLASSES);
    }

    @Test
    void reactiveServices_allPublicMethodsMustReturnUni() {
        assertThat(reactiveServiceCount())
                .as("expected at least 2 Reactive*Service classes in scope — "
                        + "if the package moved, update the ClassFileImporter")
                .isGreaterThanOrEqualTo(2);

        classes()
                .that().haveSimpleNameStartingWith("Reactive")
                .and().haveSimpleNameEndingWith("Service")
                .should(haveOnlyUniReturningPublicMethods())
                .check(SERVICE_CLASSES);
    }

    private static long reactiveServiceCount() {
        return SERVICE_CLASSES.stream()
                .filter(c -> c.getSimpleName().startsWith("Reactive")
                        && c.getSimpleName().endsWith("Service"))
                .count();
    }

    private static ArchCondition<JavaClass> haveBidirectionalMethodParity(final JavaClasses allClasses) {
        return new ArchCondition<>("have complete bidirectional method parity with its blocking counterpart") {
            @Override
            public void check(final JavaClass reactiveClass, final ConditionEvents events) {
                final String blockingName = reactiveClass.getSimpleName().replaceFirst("^Reactive", "");
                final Optional<JavaClass> blockingOpt = allClasses.stream()
                        .filter(c -> c.getSimpleName().equals(blockingName))
                        .findFirst();

                if (blockingOpt.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(reactiveClass,
                            "No blocking counterpart class named '" + blockingName + "' found for "
                                    + reactiveClass.getName()));
                    return;
                }

                final JavaClass blockingClass = blockingOpt.get();
                final Set<String> reactiveMethodNames = publicMethodNames(reactiveClass);
                final Set<String> reactiveSigs = publicMethodSignatures(reactiveClass);
                final Set<String> blockingMethodNames = publicMethodNames(blockingClass);
                final Set<String> blockingSigs = publicMethodSignatures(blockingClass);

                // Blocking → Reactive: every blocking method must have an Async counterpart
                publicMethods(blockingClass).forEach(blockingMethod -> {
                    final String expectedAsync = blockingMethod.getName() + "Async";
                    if (!reactiveMethodNames.contains(expectedAsync)) {
                        events.add(SimpleConditionEvent.violated(blockingMethod,
                                "Blocking method '" + blockingMethod.getName() + "()' in "
                                        + blockingClass.getName()
                                        + " has no reactive counterpart '"
                                        + expectedAsync + "()' in " + reactiveClass.getName()));
                    } else {
                        final String expectedAsyncSig = signature(expectedAsync, blockingMethod);
                        if (!reactiveSigs.contains(expectedAsyncSig)) {
                            events.add(SimpleConditionEvent.violated(blockingMethod,
                                    "Parameter mismatch: "
                                            + methodSignatureString(blockingMethod)
                                            + " has no matching '" + expectedAsyncSig
                                            + "' in " + reactiveClass.getName()));
                        }
                    }
                });

                // Reactive → Blocking: every Async method must have a blocking counterpart
                publicMethods(reactiveClass).forEach(reactiveMethod -> {
                    if (!reactiveMethod.getName().endsWith("Async")) {
                        events.add(SimpleConditionEvent.violated(reactiveMethod,
                                "Reactive service method '" + reactiveMethod.getName()
                                        + "()' in " + reactiveClass.getName()
                                        + " must end with 'Async'"));
                        return;
                    }
                    final String expectedBlocking = reactiveMethod.getName().replaceFirst("Async$", "");
                    if (!blockingMethodNames.contains(expectedBlocking)) {
                        events.add(SimpleConditionEvent.violated(reactiveMethod,
                                "Reactive method '" + reactiveMethod.getName()
                                        + "()' has no blocking counterpart '"
                                        + expectedBlocking + "()' in " + blockingClass.getName()));
                    } else {
                        final String expectedBlockingSig = signature(expectedBlocking, reactiveMethod);
                        if (!blockingSigs.contains(expectedBlockingSig)) {
                            events.add(SimpleConditionEvent.violated(reactiveMethod,
                                    "Parameter mismatch: "
                                            + methodSignatureString(reactiveMethod)
                                            + " has no matching '" + expectedBlockingSig
                                            + "' in " + blockingClass.getName()));
                        }
                    }
                });
            }
        };
    }

    private static ArchCondition<JavaClass> haveOnlyUniReturningPublicMethods() {
        return new ArchCondition<>("have all public methods return Uni<T>") {
            @Override
            public void check(final JavaClass reactiveClass, final ConditionEvents events) {
                publicMethods(reactiveClass).forEach(method -> {
                    if (!method.getRawReturnType().isAssignableTo(Uni.class)) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Reactive service method '" + method.getName()
                                        + "()' must return Uni<T>, but returns "
                                        + method.getRawReturnType().getName()));
                    }
                });
            }
        };
    }

    private static Stream<JavaMethod> publicMethods(final JavaClass cls) {
        return cls.getMethods().stream()
                .filter(m -> m.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(m -> !m.getModifiers().contains(JavaModifier.STATIC));
    }

    private static Set<String> publicMethodNames(final JavaClass cls) {
        return publicMethods(cls)
                .map(JavaMethod::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> publicMethodSignatures(final JavaClass cls) {
        return publicMethods(cls)
                .map(BlockingReactiveParityTest::methodSignatureString)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String signature(final String name, final JavaMethod paramsFrom) {
        return name + "(" + String.join(",", paramTypeNames(paramsFrom)) + ")";
    }

    private static String methodSignatureString(final JavaMethod method) {
        return signature(method.getName(), method);
    }

    private static List<String> paramTypeNames(final JavaMethod method) {
        return method.getRawParameterTypes().stream()
                .map(JavaClass::getName)
                .toList();
    }
}
