package io.casehub.ledger.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.runtime.privacy.LedgerErasureService.ErasureResult;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

/**
 * Integration tests for the opt-in erasure receipt feature (ledger#140).
 *
 * <p>Verifies that {@link LedgerErasureService} writes an {@link ErasureReceiptLedgerEntry}
 * when {@code casehub.ledger.erasure.receipt.enabled=true}, and that the receipt is
 * queryable via {@link ErasureReceiptRepository}.
 */
@QuarkusTest
@TestProfile(LedgerErasureReceiptIT.ErasureReceiptProfile.class)
class LedgerErasureReceiptIT {

    public static class ErasureReceiptProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "erasure-receipt-test";
        }
    }

    @Inject
    LedgerErasureService erasureService;

    @Inject
    ErasureReceiptRepository receiptRepo;

    @Inject
    LedgerEntryRepository repo;

    // ── Happy path: receipt written on successful erasure ─────────────────────

    @Test
    @Transactional
    void erase_withReceiptEnabled_resultCarriesReceiptEntryId() {
        final String actorId = "actor-" + UUID.randomUUID();
        saveEntry(actorId);

        final ErasureResult result = erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);

        assertThat(result.receiptEntryId()).isPresent();
    }

    @Test
    @Transactional
    void erase_receipt_storedWithCorrectFields() {
        final String actorId = "actor-" + UUID.randomUUID();
        saveEntry(actorId);
        saveEntry(actorId);

        final ErasureResult result = erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);

        final List<ErasureReceiptLedgerEntry> receipts =
                receiptRepo.findByErasedActorId(actorId, DEFAULT_TENANT_ID);

        assertThat(receipts).hasSize(1);
        final ErasureReceiptLedgerEntry receipt = receipts.get(0);
        assertThat(receipt.id).isEqualTo(result.receiptEntryId().orElseThrow());
        assertThat(receipt.erasedActorId).isEqualTo(actorId);
        assertThat(receipt.erasureReason).isEqualTo(ErasureReason.GDPR_ART_17_REQUEST);
        assertThat(receipt.affectedEntryCount).isEqualTo(2L);
        assertThat(receipt.mappingFound).isTrue();
        assertThat(receipt.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(receipt.tenancyId).isEqualTo(DEFAULT_TENANT_ID);
    }

    @Test
    @Transactional
    void erase_unknownActor_noMappingFound_receiptStillWritten() {
        final String actorId = "unknown-" + UUID.randomUUID();

        final ErasureResult result = erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);

        assertThat(result.mappingFound()).isFalse();
        assertThat(result.receiptEntryId()).isPresent();

        final List<ErasureReceiptLedgerEntry> receipts =
                receiptRepo.findByErasedActorId(actorId, DEFAULT_TENANT_ID);
        assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0).mappingFound).isFalse();
        assertThat(receipts.get(0).affectedEntryCount).isEqualTo(0L);
    }

    @Test
    @Transactional
    void erase_multipleErasures_receiptPerCall() {
        final String actorId = "actor-" + UUID.randomUUID();
        saveEntry(actorId);

        erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);
        erasureService.erase(actorId, ErasureReason.ACCOUNT_DELETION);

        final List<ErasureReceiptLedgerEntry> receipts =
                receiptRepo.findByErasedActorId(actorId, DEFAULT_TENANT_ID);
        assertThat(receipts).hasSize(2);
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private void saveEntry(final String actorId) {
        final TestEntry e = new TestEntry();
        e.subjectId = UUID.randomUUID();
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.HUMAN;
        e.actorRole = "tester";
        repo.save(e, DEFAULT_TENANT_ID);
    }
}
