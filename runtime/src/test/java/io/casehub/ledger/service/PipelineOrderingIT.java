package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerMerkleTree;
import io.casehub.ledger.runtime.service.intercept.ProvenanceContext;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Verifies the save pipeline ordering: enrichment runs BEFORE digest computation,
 * so supplement content (e.g. provenance) is reflected in the Merkle leaf hash.
 */
@QuarkusTest
@TestProfile(PipelineOrderingIT.PipelineOrderingProfile.class)
class PipelineOrderingIT {

    public static class PipelineOrderingProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "pipeline-ordering-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    @Inject
    ProvenanceContext provenanceContext;

    @Test
    @Transactional
    void provenanceSupplement_isReflectedInDigest() {
        provenanceContext.push("WorkItem", UUID.randomUUID().toString(), "casehub-work");
        try {
            final PlainLedgerEntry e = new PlainLedgerEntry();
            e.subjectId = UUID.randomUUID();
            e.entryType = LedgerEntryType.EVENT;
            e.actorId = "test-actor";
            e.actorType = ActorType.AGENT;
            e.actorRole = "Tester";

            final var saved = repo.save(e, TenancyConstants.DEFAULT_TENANT_ID);

            assertThat(saved.supplementJson).isNotNull();
            assertThat(saved.supplementJson).contains("casehub-work");

            final String recomputedDigest = LedgerMerkleTree.leafHash(saved);
            assertThat(saved.digest).isEqualTo(recomputedDigest);
        } finally {
            provenanceContext.pop();
        }
    }
}
