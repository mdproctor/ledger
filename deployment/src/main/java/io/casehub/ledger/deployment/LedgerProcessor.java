package io.casehub.ledger.deployment;

import java.util.HashSet;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.flyway.runtime.FlywayBuildTimeConfig;

import io.casehub.ledger.runtime.service.ReactiveAgentSignatureVerificationService;
import io.casehub.ledger.runtime.service.ReactiveKeyRotationService;

/**
 * Quarkus build-time processor for the Ledger extension.
 *
 * <p>
 * Registers the "ledger" feature, gates the reactive service tier behind
 * {@code casehub.ledger.reactive.enabled=true}, and validates that consumers
 * have configured {@code classpath:db/ledger/migration} in their Flyway locations.
 */
class LedgerProcessor {

    private static final Logger LOG = Logger.getLogger(LedgerProcessor.class);
    private static final String FEATURE = "ledger";
    private static final String LEDGER_MIGRATION_PATH = "db/ledger/migration";

    static final DotName JAVA_LANG_OBJECT = DotName.createSimple("java.lang.Object");
    static final DotName LEDGER_ENTRY = DotName.createSimple(
            "io.casehub.ledger.runtime.model.LedgerEntry");
    static final DotName CROSS_TENANT = DotName.createSimple(
            "io.casehub.ledger.runtime.qualifier.CrossTenant");
    static final DotName REQUEST_SCOPED = DotName.createSimple(
            "jakarta.enterprise.context.RequestScoped");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem registerMigrationResources() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("db/ledger/migration/*.sql")
                .build();
    }

    /**
     * Excludes reactive-tier service beans from CDI when
     * {@code casehub.ledger.reactive.enabled} is not {@code true}.
     *
     * <p>
     * This is a build-time decision — reactive beans are absent from the CDI graph
     * in JDBC-only consumers, preventing unsatisfied-dependency failures at
     * augmentation time.
     */
    @BuildStep
    void excludeReactiveBeans(
            final LedgerBuildTimeConfig config,
            final BuildProducer<ExcludedTypeBuildItem> excluded) {
        if (!config.reactive().enabled()) {
            excluded.produce(
                    new ExcludedTypeBuildItem(ReactiveKeyRotationService.class.getName()));
            excluded.produce(
                    new ExcludedTypeBuildItem(ReactiveAgentSignatureVerificationService.class.getName()));
        }
    }

    /**
     * Warns at build time if no Flyway datasource is configured with
     * {@code classpath:db/ledger/migration}. Without this location, ledger tables
     * will not be created — a misconfiguration that surfaces only at runtime.
     *
     * <p>Emits a warning rather than failing the build so that misconfigured
     * environments surface at runtime with a clear Hibernate schema error rather
     * than a hard build gate. DDL-generation test environments will also see this
     * warning; they can safely ignore it.
     */
    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void validateFlywayMigrationLocation(final FlywayBuildTimeConfig flywayBuildTimeConfig) {
        final boolean hasLedgerLocation = flywayBuildTimeConfig.datasources().values().stream()
                .flatMap(ds -> ds.locations().stream())
                .map(loc -> loc.replace("classpath:", "").strip())
                .anyMatch(loc -> loc.equals(LEDGER_MIGRATION_PATH)
                        || loc.endsWith("/" + LEDGER_MIGRATION_PATH));

        if (!hasLedgerLocation) {
            LOG.warn("""
                    casehub-ledger is on the classpath but classpath:db/ledger/migration is not \
                    configured in any Flyway datasource locations. \
                    Ledger tables will not be created. \
                    Add classpath:db/ledger/migration to quarkus.flyway.locations \
                    (or quarkus.flyway.<named-datasource>.locations when using a named datasource).\
                    """);
        }
    }

    /**
     * Scans all {@link io.casehub.ledger.runtime.model.LedgerEntry} subclasses for fields
     * that shadow base class fields. Field shadowing in a JOINED-inheritance JPA hierarchy
     * causes duplicate column mapping, silent data loss, and Hibernate runtime errors.
     *
     * <p>
     * The check walks the full ancestor chain from each subclass up to {@code LedgerEntry}
     * and collects all ancestor field names. Any subclass field whose name matches an
     * ancestor field is a deployment error.
     */
    @BuildStep
    void validateLedgerEntryFieldShadowing(
            final CombinedIndexBuildItem combinedIndex,
            final ValidationPhaseBuildItem validation,
            final BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {

        final IndexView index = combinedIndex.getIndex();

        for (final ClassInfo subclass : index.getAllKnownSubclasses(LEDGER_ENTRY)) {
            final Set<String> ancestorFields = collectAncestorFields(index, subclass.superName());
            for (final FieldInfo field : subclass.fields()) {
                if (ancestorFields.contains(field.name())) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new IllegalStateException(
                                    "LedgerEntry subclass " + subclass.name()
                                            + " shadows base-class field '" + field.name()
                                            + "'. Remove the shadowing field — JPA JOINED "
                                            + "inheritance maps the base field automatically.")));
                }
            }
        }
    }

    /**
     * Collects all field names declared on {@code className} and its ancestors up to
     * (and including) {@code LedgerEntry}. Returns an empty set if the class is not
     * found in the index or is not in the LedgerEntry hierarchy.
     */
    static Set<String> collectAncestorFields(final IndexView index, DotName className) {
        final Set<String> fields = new HashSet<>();
        while (className != null && !className.equals(JAVA_LANG_OBJECT)) {
            final ClassInfo info = index.getClassByName(className);
            if (info == null) {
                break;
            }
            for (final FieldInfo field : info.fields()) {
                fields.add(field.name());
            }
            className = info.superName();
        }
        return fields;
    }

    /**
     * Rejects {@code @RequestScoped} beans that inject {@code @CrossTenant}-qualified
     * repositories. Cross-tenant repositories bypass tenant isolation — they must not
     * be reachable from request-scoped code, which runs in a per-tenant context.
     *
     * <p>
     * Allowed scopes: {@code @ApplicationScoped}, {@code @Singleton}, {@code @Dependent}
     * (when injected into a wider scope). {@code @RequestScoped} is rejected because it
     * implies the bean serves tenant-scoped HTTP requests.
     */
    @BuildStep
    void validateCrossTenantScope(
            final BeanDiscoveryFinishedBuildItem beanDiscovery,
            final BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {

        for (final InjectionPointInfo ip : beanDiscovery.getInjectionPoints()) {
            final boolean hasCrossTenant = ip.getRequiredQualifiers().stream()
                    .anyMatch(q -> q.name().equals(CROSS_TENANT));
            if (!hasCrossTenant) {
                continue;
            }

            final BeanInfo targetBean = ip.getTargetBean().orElse(null);
            if (targetBean == null) {
                continue;
            }

            if (REQUEST_SCOPED.equals(targetBean.getScope().getDotName())) {
                errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                        new IllegalStateException(
                                "@CrossTenant injection in @RequestScoped bean "
                                        + targetBean.getBeanClass()
                                        + " is not allowed. Cross-tenant repositories bypass "
                                        + "tenant isolation and must not be injected into "
                                        + "request-scoped beans. Use @ApplicationScoped or "
                                        + "@Singleton instead.")));
            }
        }
    }
}
