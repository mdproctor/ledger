package io.casehub.ledger.core.api;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.view.AppendEntryRequest;
import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@McpDomain(value = "ledger/entries", basePath = "/api/v1/ledger")
public class LedgerEntryApiCore {

    private final LedgerEntryRepository repository;
    private final LedgerAppender appender;

    public LedgerEntryApiCore(LedgerEntryRepository repository, LedgerAppender appender) {
        this.repository = repository;
        this.appender = appender;
    }

    @PlatformQuery("List ledger entries by subject or actor with optional time range")
    public LedgerEntryPage listEntries(UUID subjectId, String actorId,
                                        @ContextParam("tenancyId") String tenancyId,
                                        Instant from, Instant to,
                                        Integer offset, Integer limit) {
        String tid = defaultTenancyId(tenancyId);
        int off = offset != null ? offset : 0;
        int lim = limit != null ? limit : 20;

        List<? extends LedgerEntry> entries;
        if (subjectId != null) {
            entries = (from != null && to != null)
                    ? repository.findBySubjectIdAndTimeRange(subjectId, from, to, tid)
                    : repository.findBySubjectId(subjectId, tid);
        } else if (actorId != null) {
            Instant start = from != null ? from : Instant.EPOCH;
            Instant end = to != null ? to : Instant.now();
            entries = repository.findByActorId(actorId, start, end, tid);
        } else {
            entries = List.of();
        }

        List<LedgerEntryView> all = entries.stream().map(LedgerEntryApiCore::toView).toList();
        int total = all.size();
        int endIdx = Math.min(off + lim, total);
        List<LedgerEntryView> items = off < total ? all.subList(off, endIdx) : List.of();
        return new LedgerEntryPage(items, total, endIdx < total);
    }

    @PlatformQuery("Get a single ledger entry by ID")
    public LedgerEntryView getEntry(@PathParam UUID id,
                                     @ContextParam("tenancyId") String tenancyId) {
        return repository.findEntryById(id, defaultTenancyId(tenancyId))
                .map(LedgerEntryApiCore::toView).orElse(null);
    }

    @PlatformQuery("Get entries causally triggered by this entry")
    public List<LedgerEntryView> getCausedBy(@PathParam UUID id,
                                              @ContextParam("tenancyId") String tenancyId) {
        return repository.findCausedBy(id, defaultTenancyId(tenancyId))
                .stream().map(LedgerEntryApiCore::toView).toList();
    }

    @PlatformMutation("Append a new audit entry to the ledger")
    public LedgerEntryView appendEntry(AppendEntryRequest request,
                                        @ContextParam("tenancyId") String tenancyId) {
        String tid = defaultTenancyId(tenancyId);
        AuditRecord record = AuditRecord.event(request.actorId(), request.subjectId());
        if (request.actorRole() != null) record = record.withActorRole(request.actorRole());
        if (request.metadata() != null) record = record.withMetadata(request.metadata());
        if (request.domainData() != null) record = record.withDomainData(request.domainData());
        UUID entryId = appender.append(record, tid);
        return repository.findEntryById(entryId, tid)
                .map(LedgerEntryApiCore::toView).orElseThrow();
    }

    public static LedgerEntryView toView(LedgerEntry e) {
        return new LedgerEntryView(
                e.id, e.subjectId, e.tenancyId, e.sequenceNumber,
                e.entryType != null ? e.entryType.name() : null,
                e.actorId,
                e.actorType != null ? e.actorType.name() : null,
                e.actorRole, e.occurredAt, e.digest, e.traceId,
                e.causedByEntryId, e.metadata, e.domainData);
    }

    public static String defaultTenancyId(String tenancyId) {
        return tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
    }
}
