package io.casehub.ledger.annotations.deployment;

import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.ComplianceSupplement;
import io.casehub.ledger.annotations.ConfidenceScore;
import io.casehub.ledger.annotations.DecisionContext;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.TenancyId;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.jpa.PlainLedgerEntry;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceSupplementTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(ComplianceService.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=h2\n"
                            + "quarkus.datasource.jdbc.url=jdbc:h2:mem:compliancesupplementtestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1\n"
                            + "quarkus.datasource.username=sa\n"
                            + "quarkus.datasource.password=\n"
                            + "quarkus.hibernate-orm.database.generation=none\n"
                            + "quarkus.flyway.migrate-at-start=true\n"
                            + "quarkus.flyway.locations=classpath:db/ledger/migration\n"
                            + "casehub.ledger.outcome.default-attestor-id=test-attestor\n"),
                            "application.properties"));

    @Inject
    ComplianceService service;

    @Inject
    LedgerEntryRepository repo;

    @Test
    void complianceSupplementAttachesStaticAndDynamicFields() {
        UUID subjectId = UUID.randomUUID();
        service.assess(subjectId, "agent-1", "default",
                "{\"riskScore\":42}", 0.91);

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        var compliance = entries.get(0).compliance();
        assertThat(compliance).isPresent();
        assertThat(compliance.get().algorithmRef).isEqualTo("risk-classifier-v2");
        assertThat(compliance.get().contestationUri).isEqualTo("https://example.com/challenge");
        assertThat(compliance.get().humanOverrideAvailable).isTrue();
        assertThat(compliance.get().planRef).isEqualTo("policy-v1");
        assertThat(compliance.get().confidenceScore).isEqualTo(0.91);
        assertThat(compliance.get().decisionContext).isEqualTo("{\"riskScore\":42}");
    }

    @Test
    void complianceSupplementWithoutDynamicFields() {
        UUID subjectId = UUID.randomUUID();
        service.assessSimple(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        var compliance = entries.get(0).compliance();
        assertThat(compliance).isPresent();
        assertThat(compliance.get().algorithmRef).isEqualTo("simple-model");
        assertThat(compliance.get().confidenceScore).isNull();
        assertThat(compliance.get().decisionContext).isNull();
    }

    @Test
    void standaloneComplianceSupplementWithoutAudited() {
        UUID subjectId = UUID.randomUUID();
        service.assessStandalone(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        var compliance = entries.get(0).compliance();
        assertThat(compliance).isPresent();
        assertThat(compliance.get().algorithmRef).isEqualTo("standalone-model");
        assertThat(compliance.get().contestationUri).isEqualTo("https://example.com/standalone");
        assertThat(compliance.get().humanOverrideAvailable).isTrue();
    }


    @ApplicationScoped
    public static class ComplianceService {

        @Audited
        @ComplianceSupplement(
                algorithmRef = "risk-classifier-v2",
                contestationUri = "https://example.com/challenge",
                humanOverrideAvailable = true,
                planRef = "policy-v1")
        public String assess(@SubjectId UUID caseId,
                             @ActorId String agentId,
                             @TenancyId String tenancyId,
                             @DecisionContext String contextJson,
                             @ConfidenceScore double confidence) {
            return "assessed";
        }

        @Audited
        @ComplianceSupplement(algorithmRef = "simple-model")
        public String assessSimple(@SubjectId UUID caseId,
                                   @ActorId String agentId,
                                   @TenancyId String tenancyId) {
            return "assessed";
        }

        @ComplianceSupplement(
                algorithmRef = "standalone-model",
                contestationUri = "https://example.com/standalone",
                humanOverrideAvailable = true)
        public void assessStandalone(@SubjectId UUID caseId,
                                     @ActorId String agentId,
                                     @TenancyId String tenancyId) {
            final var entry = new PlainLedgerEntry();
            entry.subjectId = caseId;
            entry.actorId   = agentId;
            entry.entryType = io.casehub.ledger.api.model.LedgerEntryType.EVENT;
            repo.save(entry, tenancyId);
        }

        @Inject
        LedgerEntryRepository repo;
    }
}
