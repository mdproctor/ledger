package io.casehub.ledger.service.supplement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.supplement.LedgerSupplementSerializer;
import io.casehub.ledger.runtime.model.supplement.JpaCompensationSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;

/**
 * Unit tests for {@link LedgerSupplementSerializer} — no Quarkus runtime, no CDI.
 */
class LedgerSupplementSerializerTest {

    @Test
    void toJson_nullList_returnsNull() {
        assertThat(LedgerSupplementSerializer.toJson(null)).isNull();
    }

    @Test
    void toJson_emptyList_returnsNull() {
        assertThat(LedgerSupplementSerializer.toJson(List.of())).isNull();
    }

    @Test
    void toJson_complianceSupplement_containsTypeKey() {
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = "model-v1";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).isNotNull();
        assertThat(json).contains("\"COMPLIANCE\"");
        assertThat(json).contains("\"algorithmRef\":\"model-v1\"");
    }

    @Test
    void toJson_nullFieldsOmitted() {
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = "rule-engine-v2";
        // confidenceScore, contestationUri, humanOverrideAvailable all null

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).contains("algorithmRef");
        assertThat(json).doesNotContain("confidenceScore");
        assertThat(json).doesNotContain("contestationUri");
        assertThat(json).doesNotContain("humanOverrideAvailable");
    }

    @Test
    void toJson_allComplianceFields_serialisedCorrectly() {
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.planRef = "policy-2026-q1";
        cs.rationale = "Risk threshold exceeded";
        cs.algorithmRef = "gpt-4o";
        cs.confidenceScore = 0.92;
        cs.contestationUri = "https://example.com/challenge";
        cs.humanOverrideAvailable = true;
        cs.decisionContext = "{\"riskScore\":77}";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).contains("\"planRef\":\"policy-2026-q1\"");
        assertThat(json).contains("\"rationale\":\"Risk threshold exceeded\"");
        assertThat(json).contains("\"algorithmRef\":\"gpt-4o\"");
        assertThat(json).contains("\"confidenceScore\":0.92");
        assertThat(json).contains("\"contestationUri\":\"https://example.com/challenge\"");
        assertThat(json).contains("\"humanOverrideAvailable\":true");
        assertThat(json).contains("\"decisionContext\":\"{\\\"riskScore\\\":77}\"");
    }

    @Test
    void toJson_provenanceSupplement_serialisedCorrectly() {
        final JpaProvenanceSupplement ps = new JpaProvenanceSupplement();
        ps.sourceEntityId = "wf-123";
        ps.sourceEntityType = "Flow:WorkflowInstance";
        ps.sourceEntitySystem = "quarkus-flow";

        final String json = LedgerSupplementSerializer.toJson(List.of(ps));

        assertThat(json).contains("\"PROVENANCE\"");
        assertThat(json).contains("\"sourceEntitySystem\":\"quarkus-flow\"");
    }

    @Test
    void toJson_multipleSupplements_allPresent() {
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = "v1";
        final JpaProvenanceSupplement ps = new JpaProvenanceSupplement();
        ps.sourceEntitySystem = "quarkus-flow";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs, ps));

        assertThat(json).contains("\"COMPLIANCE\"");
        assertThat(json).contains("\"PROVENANCE\"");
        assertThat(json).contains("algorithmRef");
        assertThat(json).contains("quarkus-flow");
    }

    @Test
    void toJson_compensationSupplement_containsTypeKey() {
        final JpaCompensationSupplement cs = new JpaCompensationSupplement();
        cs.originalEntryId = java.util.UUID.randomUUID();
        cs.compensationMode = "automated";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).isNotNull();
        assertThat(json).contains("\"COMPENSATION\"");
        assertThat(json).contains("\"compensationMode\":\"automated\"");
    }

    @Test
    void toJson_allCompensationFields_serialisedCorrectly() {
        final java.util.UUID origId = java.util.UUID.randomUUID();
        final JpaCompensationSupplement cs = new JpaCompensationSupplement();
        cs.originalEntryId = origId;
        cs.compensationReason = "Clinical trial withdrawn";
        cs.regulatoryBasis = "GDPR Art.17";
        cs.compensationMode = "human-driven";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).contains("\"originalEntryId\":\"" + origId + "\"");
        assertThat(json).contains("\"compensationReason\":\"Clinical trial withdrawn\"");
        assertThat(json).contains("\"regulatoryBasis\":\"GDPR Art.17\"");
        assertThat(json).contains("\"compensationMode\":\"human-driven\"");
    }

    @Test
    void toJson_compensationNullFieldsOmitted() {
        final JpaCompensationSupplement cs = new JpaCompensationSupplement();
        cs.originalEntryId = java.util.UUID.randomUUID();
        cs.compensationMode = "automated";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs));

        assertThat(json).contains("compensationMode");
        assertThat(json).doesNotContain("compensationReason");
        assertThat(json).doesNotContain("regulatoryBasis");
    }

    @Test
    void toJson_allThreeSupplements_allPresent() {
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = "v1";
        final JpaProvenanceSupplement ps = new JpaProvenanceSupplement();
        ps.sourceEntitySystem = "quarkus-flow";
        final JpaCompensationSupplement comp = new JpaCompensationSupplement();
        comp.originalEntryId = java.util.UUID.randomUUID();
        comp.compensationMode = "automated";

        final String json = LedgerSupplementSerializer.toJson(List.of(cs, ps, comp));

        assertThat(json).contains("\"COMPLIANCE\"");
        assertThat(json).contains("\"PROVENANCE\"");
        assertThat(json).contains("\"COMPENSATION\"");
    }
}
