package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntryArchiveRecord;
import io.casehub.ledger.runtime.model.LedgerMerkleFrontier;
import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.ledger.api.model.supplement.LedgerSupplement;
import io.casehub.ledger.api.model.supplement.ComplianceSupplement;
import io.casehub.ledger.api.model.supplement.ProvenanceSupplement;

/**
 * Structural tests ensuring all entities are plain @Entity POJOs.
 * Fails if any entity re-introduces PanacheEntityBase — prevents regression.
 */
class PlainEntityTest {

    /** JPA entities — must have @Entity */
    private static final List<Class<?>> JPA_ENTITIES = List.of(
            LedgerMerkleFrontier.class,
            LedgerAttestation.class,
            ActorTrustScore.class,
            LedgerEntryArchiveRecord.class,
            JpaComplianceSupplement.class,
            JpaProvenanceSupplement.class);

    /** @MappedSuperclass types — must have @MappedSuperclass */
    private static final List<Class<?>> MAPPED_SUPERCLASSES = List.of(
            LedgerEntry.class,
            LedgerSupplement.class,
            ComplianceSupplement.class,
            ProvenanceSupplement.class);

    @Test
    void allEntities_doNotExtendPanacheEntityBase() {
        for (Class<?> entity : JPA_ENTITIES) {
            boolean extendsPanache = false;
            Class<?> c = entity.getSuperclass();
            while (c != null && c != Object.class) {
                if (c.getName().contains("PanacheEntityBase")) {
                    extendsPanache = true;
                    break;
                }
                c = c.getSuperclass();
            }
            assertThat(extendsPanache)
                    .as(entity.getSimpleName() + " must not extend PanacheEntityBase")
                    .isFalse();
        }
    }

    @Test
    void jpaEntities_haveEntityAnnotation() {
        for (Class<?> entity : JPA_ENTITIES) {
            assertThat(entity.isAnnotationPresent(jakarta.persistence.Entity.class))
                    .as(entity.getSimpleName() + " must have @Entity")
                    .isTrue();
        }
    }

    @Test
    void mappedSuperclasses_areNotEntities() {
        // @MappedSuperclass annotation may be stripped by Hibernate bytecode enhancement
        // at runtime — verify these types are NOT @Entity (they define column mappings
        // inherited by JPA entity subclasses, not standalone tables).
        for (Class<?> cls : MAPPED_SUPERCLASSES) {
            assertThat(cls.isAnnotationPresent(jakarta.persistence.Entity.class))
                    .as(cls.getSimpleName() + " must not be @Entity (it is @MappedSuperclass in source)")
                    .isFalse();
        }
    }
}
