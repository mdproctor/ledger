package io.casehub.ledger.graphql;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.graphql.dto.AppendLedgerEntryInput;
import io.casehub.ledger.graphql.dto.CreateAttestationInput;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

@GraphQLApi
@McpDomain(value = "ledger/model", app = "ledger", summary = "Ledger domain model enrichment and state")
@ApplicationScoped
public class LedgerMutationResolver {

    @Inject LedgerAppender ledgerAppender;
    @Inject LedgerEntryRepository entryRepository;
    @Inject OutcomeRecorder outcomeRecorder;
    @Inject CurrentPrincipal currentPrincipal;

    @Mutation
    @Description("Append a new audit entry to the ledger with optional domain data")
    public io.casehub.ledger.graphql.dto.LedgerEntryType appendLedgerEntry(AppendLedgerEntryInput input) {
        String tenancyId = currentPrincipal.tenancyId();
        String actorId = input.actorId() != null ? input.actorId() : currentPrincipal.actorId();

        AuditRecord record = AuditRecord.event(actorId, input.subjectId());
        if (input.actorRole() != null) {
            record = record.withActorRole(input.actorRole());
        }
        if (input.metadata() != null) {
            record = record.withMetadata(input.metadata());
        }
        if (input.domainData() != null) {
            record = record.withDomainData(input.domainData().value());
        }

        UUID entryId = ledgerAppender.append(record, tenancyId);
        return entryRepository.findEntryById(entryId, tenancyId)
                .map(io.casehub.ledger.graphql.dto.LedgerEntryType::from)
                .orElseThrow();
    }

    @Mutation
    @Description("Create an attestation on an existing ledger entry — records a peer verdict with confidence")
    public boolean createAttestation(CreateAttestationInput input) {
        outcomeRecorder.addAttestation(
                input.entryId(),
                AttestationVerdict.valueOf(input.verdict()),
                input.confidence(),
                input.capabilityTag());
        return true;
    }
}
