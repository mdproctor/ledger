package io.casehub.ledger.deployment;

import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Jandex-based field-shadowing detection algorithm in
 * {@link LedgerProcessor#collectAncestorFields(org.jboss.jandex.IndexView, DotName)}.
 *
 * <p>
 * These tests build a synthetic Jandex index from fixture classes and verify
 * that the algorithm correctly identifies shadowing fields versus clean subclass
 * fields. No Quarkus augmentation is needed — the algorithm is pure Jandex.
 */
class LedgerProcessorFieldShadowingTest {

    // ── Fixture hierarchy ────────────────────────────────────────────────────

    /** Simulates LedgerEntry with core fields. */
    @SuppressWarnings("unused")
    static class FakeBase {
        public String actorId;
        public String tenancyId;
        public int sequenceNumber;
    }

    /** Clean subclass — no shadowing. */
    @SuppressWarnings("unused")
    static class CleanSubclass extends FakeBase {
        public String customField;
    }

    /** Bad subclass — shadows actorId from FakeBase. */
    @SuppressWarnings("unused")
    static class ShadowingSubclass extends FakeBase {
        public String actorId; // shadows FakeBase.actorId
        public String otherField;
    }

    /** Intermediate class in a deeper hierarchy. */
    @SuppressWarnings("unused")
    static class IntermediateSubclass extends FakeBase {
        public String intermediateField;
    }

    /** Shadows a field from the grandparent (FakeBase), not the direct parent. */
    @SuppressWarnings("unused")
    static class GrandchildShadowing extends IntermediateSubclass {
        public String tenancyId; // shadows FakeBase.tenancyId
    }

    /** Clean grandchild — no shadowing at any level. */
    @SuppressWarnings("unused")
    static class CleanGrandchild extends IntermediateSubclass {
        public String grandchildField;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void cleanSubclass_noShadowingDetected() throws Exception {
        final Index index = buildIndex(FakeBase.class, CleanSubclass.class);
        final ClassInfo subclass = index.getClassByName(CleanSubclass.class);

        final Set<String> ancestorFields = LedgerProcessor.collectAncestorFields(
                index, subclass.superName());

        // Ancestor fields should contain the base fields
        assertThat(ancestorFields).contains("actorId", "tenancyId", "sequenceNumber");

        // No subclass field should collide
        for (final var field : subclass.fields()) {
            assertThat(ancestorFields).doesNotContain(field.name());
        }
    }

    @Test
    void shadowingSubclass_directShadowDetected() throws Exception {
        final Index index = buildIndex(FakeBase.class, ShadowingSubclass.class);
        final ClassInfo subclass = index.getClassByName(ShadowingSubclass.class);

        final Set<String> ancestorFields = LedgerProcessor.collectAncestorFields(
                index, subclass.superName());

        // actorId is declared on FakeBase and also on ShadowingSubclass
        assertThat(subclass.fields().stream().map(f -> f.name()))
                .contains("actorId");
        assertThat(ancestorFields).contains("actorId");
    }

    @Test
    void grandchildShadowing_transitiveShadowDetected() throws Exception {
        final Index index = buildIndex(
                FakeBase.class, IntermediateSubclass.class, GrandchildShadowing.class);
        final ClassInfo subclass = index.getClassByName(GrandchildShadowing.class);

        final Set<String> ancestorFields = LedgerProcessor.collectAncestorFields(
                index, subclass.superName());

        // tenancyId lives on FakeBase, two levels up
        assertThat(ancestorFields).contains("tenancyId");
        assertThat(subclass.fields().stream().map(f -> f.name()))
                .contains("tenancyId");
    }

    @Test
    void cleanGrandchild_noShadowing() throws Exception {
        final Index index = buildIndex(
                FakeBase.class, IntermediateSubclass.class, CleanGrandchild.class);
        final ClassInfo subclass = index.getClassByName(CleanGrandchild.class);

        final Set<String> ancestorFields = LedgerProcessor.collectAncestorFields(
                index, subclass.superName());

        for (final var field : subclass.fields()) {
            assertThat(ancestorFields)
                    .as("field '%s' should not shadow any ancestor", field.name())
                    .doesNotContain(field.name());
        }
    }

    @Test
    void collectAncestorFields_unknownClass_returnsEmptySet() throws Exception {
        final Index emptyIndex = Index.of(new Class<?>[0]);
        final Set<String> result = LedgerProcessor.collectAncestorFields(
                emptyIndex, DotName.createSimple("com.nonexistent.Missing"));

        assertThat(result).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Index buildIndex(final Class<?>... classes) throws Exception {
        final Indexer indexer = new Indexer();
        for (final Class<?> clazz : classes) {
            indexer.indexClass(clazz);
        }
        return indexer.complete();
    }
}
