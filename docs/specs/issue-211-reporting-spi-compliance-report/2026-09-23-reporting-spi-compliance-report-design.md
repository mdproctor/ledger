# Design Spec — Reporting SPI: EU AI Act Art.12 Compliance Report + Audit Trail Export

**Date:** 2026-09-23
**Issue:** #211 — feat: Reporting SPI — ReportRenderer + EU AI Act Art.12 compliance report
**Status:** Draft

---

## Problem

EU AI Act Article 12 requires high-risk AI systems to provide structured compliance
reports and complete audit trails on demand. The ledger stores the right data
(`ComplianceSupplement`, `LedgerEntry`, Merkle verification, PROV-O export) but lacks:

1. **Tenancy-level aggregation** — existing `LedgerComplianceReportService` only supports
   per-actor and per-subject reports. Auditors need "show me everything in this trial."
2. **Structured audit trail export** — no service aggregates entries + Merkle verification +
   PROV-O export across all subjects in a tenancy.
3. **PDF rendering** — auditors need printable reports, not just JSON/CSV.

These capabilities are reusable across all ledger consumers (clinical, AML, fsitrading).

---

## Design Constraint

> **If a consumer does not add `casehub-ledger-reporting`, nothing changes.** The data
> services (tenancy-level queries, enhanced compliance reports, audit trail export) are
> available in `runtime` for any consumer. PDF/HTML rendering is opt-in via the
> `reporting` module.

---

## Architecture

Three layers, two modules:

- **`casehub-ledger` (runtime)** — data aggregation services. Enhanced
  `LedgerComplianceReportService` with tenancy-level reporting. New
  `AuditTrailExportService`. New repository query for tenancy-scoped reads.
- **`casehub-ledger-reporting` (new, opt-in plain JAR)** — presentation layer.
  Qute HTML templates, `PdfGenerator` integration, content negotiation helper.
  Follows the `rest/` and `graphql/` module pattern.
- **Consumer app** — owns REST endpoints, wires `LedgerReportingService` (or
  uses the data services directly with its own rendering).

PDF generation uses the platform's existing `PdfGenerator` SPI
(`io.casehub.platform.api.pdf.PdfGenerator`). Consumers who want PDF output
add `platform-pdf` to their classpath. Without it, the `NoOpPdfGenerator`
default bean is active and `renderPdf()` throws `IllegalStateException`.

---

## Part 1 — Repository Layer

### 1a. `LedgerEntryRepository` — two new default methods

Both are `default` methods returning `List.of()`, consistent with the existing SPI pattern
(`streamBySubjectId`, `findBySubjectIdPaged`, `countByActorAndVerdict`). Only
`JpaLedgerEntryRepository` and `InMemoryLedgerEntryRepository` provide real implementations.

Parameter order follows the established convention: `tenancyId` is always last.

```java
/**
 * Return all ledger entries for the given tenancy whose {@code occurredAt} falls
 * within [{@code from}, {@code to}] inclusive, ordered by {@code occurredAt} ascending.
 *
 * @param from start of the time range (inclusive)
 * @param to end of the time range (inclusive)
 * @param tenancyId the tenant scope
 * @return ordered list; empty if none match
 */
default List<LedgerEntry> findByTimeRange(Instant from, Instant to, String tenancyId) {
    return List.of();
}

/**
 * Return all distinct subject IDs that have at least one entry in the given tenancy.
 *
 * @param tenancyId the tenant scope
 * @return list of distinct subject UUIDs; empty if none exist
 */
default List<UUID> findDistinctSubjectIds(String tenancyId) {
    return List.of();
}
```

### 1b. `@NamedQuery` declarations on `JpaLedgerEntry`

Per protocol `ledger-entry-named-query`, all JPQL must be `@NamedQuery`:

```java
@NamedQuery(name = "LedgerEntry.findByTimeRange",
    query = "SELECT e FROM LedgerEntry e WHERE e.tenancyId = :tenancyId " +
            "AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
@NamedQuery(name = "LedgerEntry.findDistinctSubjectIds",
    query = "SELECT DISTINCT e.subjectId FROM LedgerEntry e WHERE e.tenancyId = :tenancyId")
```

Note: JPQL uses `FROM LedgerEntry` (the `@Entity(name)` value), not `FROM JpaLedgerEntry`
(the Java class name). All 16 existing `@NamedQuery` declarations follow this convention.

### 1c. Implementations required

| Repository | Action |
|---|---|
| `JpaLedgerEntryRepository` | Implement via `em.createNamedQuery()` |
| `InMemoryLedgerEntryRepository` | Filter `allEntries()` by tenancyId + time range |

`NoOpLedgerEntryRepository` (ledger-core) and `NoOpLedgerEntryRepository` (testing) inherit
the `default` methods and need no changes.

---

## Part 2 — Enhanced Report Models (`ledger-core`)

### 2a. `ComplianceReport` — evolve existing record

```java
public record ComplianceReport(
        String actorId,
        UUID subjectId,
        String tenancyId,
        Instant from,
        Instant to,
        int totalDecisions,
        List<DecisionRecord> decisions,
        ComplianceSummary summary,
        String merkleRootAtGeneration) {

    public String format(final ReportFormat format) { /* existing, enhanced for new fields */ }
}
```

New fields: `tenancyId` (always present), `summary` (aggregate statistics).
For per-actor reports, `subjectId` is null. For per-subject reports, `actorId` is null.
For tenancy-level reports, both `actorId` and `subjectId` are null.

### 2b. `DecisionRecord` — enrich

```java
public record DecisionRecord(
        UUID entryId,
        String entryType,
        Instant occurredAt,
        String actorId,
        String algorithmRef,
        Double confidenceScore,
        String planRef,
        String contestationUri,
        Boolean humanOverrideAvailable,
        String sourceEntityType,
        String sourceEntityId) {
}
```

New fields: `entryType` (JPA discriminator value — the domain type name, e.g.
`"PlainLedgerEntry"`, `"WorkItemLedgerEntry"` — not the `LedgerEntryType` enum),
`actorId` (who made the decision), `planRef` (from `ComplianceSupplement`).

`decisionsByType` in `ComplianceSummary` counts by this same discriminator value.

### 2c. `ComplianceSummary` — new record

```java
public record ComplianceSummary(
        int totalDecisions,
        int aiAssistedDecisions,
        int humanOverrideCount,
        Map<String, Integer> decisionsByType) {
}
```

`aiAssistedDecisions`: count of entries where `algorithmRef` is non-null.
`humanOverrideCount`: count of entries where `humanOverrideAvailable` is true.
`decisionsByType`: frequency by discriminator value (domain type name).

Risk classification is omitted — `ComplianceSupplement.detail` is a free-text field
with no schema, so parsing it for `riskLevel` would be fragile. If risk classification
becomes important, it should be a first-class field on `ComplianceSupplement` (future work).

### 2d. `AuditTrailExport` — new records

```java
public record AuditTrailExport(
        String tenancyId,
        Instant from,
        Instant to,
        Instant generatedAt,
        List<SubjectAuditTrail> subjects,
        VerificationSummary verification) {
}

public record SubjectAuditTrail(
        UUID subjectId,
        List<AuditEntry> entries,
        String merkleRoot,
        boolean chainValid,
        String provJsonLd) {
}

public record AuditEntry(
        UUID entryId,
        String entryType,
        Instant occurredAt,
        String actorId,
        String digest,
        int sequenceNumber) {
}

public record VerificationSummary(
        int totalSubjects,
        int validChains,
        int invalidChains,
        int totalEntries) {
}
```

### 2e. `ReportFormat` — unchanged

`ReportFormat` stays as-is (PLAIN_JSON, JSON_LD, CSV). It is a text-format enum used by
`ComplianceReport.format()` which returns `String`. Adding PDF would break that contract
(PDF is `byte[]`, not `String`).

The reporting module introduces its own `OutputFormat` enum that includes PDF and HTML:

```java
// in io.casehub.ledger.reporting
public enum OutputFormat {
    JSON, JSON_LD, CSV, HTML, PDF
}
```

---

## Part 3 — Data Services (`runtime`)

### 3a. `LedgerComplianceReportService` — add `reportForTenancy()`

```java
@Transactional
public ComplianceReport reportForTenancy(
        final String tenancyId, final Instant from, final Instant to) {
    final List<LedgerEntry> entries = repo.findByTimeRange(from, to, tenancyId);
    final List<DecisionRecord> decisions = entries.stream()
            .filter(e -> e.compliance().isPresent())
            .map(this::toDecisionRecord)
            .toList();
    final ComplianceSummary summary = buildSummary(decisions);
    final String merkleRoot = buildTenancyMerkleRoot(entries, tenancyId);
    return new ComplianceReport(null, null, tenancyId, from, to,
            decisions.size(), decisions, summary, merkleRoot);
}
```

**`buildTenancyMerkleRoot`** — same algorithm as the existing `buildActorMerkleRoot`:
extracts distinct `subjectId` values from the entry list, calls
`verificationService.treeRoot(subjectId, tenancyId)` for each, and joins them as
semicolon-separated `subjectId=root` pairs. Returns null if no subjects have Merkle roots.

Update existing `reportForActor()` and `reportForSubject()` to populate `tenancyId`
and `summary` in their return values.

Update `toDecisionRecord()` to extract additional fields (`entryType` via Hibernate
discriminator value, `actorId`, `planRef`) from the entry and its compliance supplement.

Add `buildSummary(List<DecisionRecord>)` — pure computation, builds `ComplianceSummary`
from the decision list.

### 3b. `AuditTrailExportService` — new CDI bean

```java
@ApplicationScoped
public class AuditTrailExportService {

    @Inject LedgerEntryRepository repo;
    @Inject LedgerVerificationService verificationService;
    @Inject LedgerProvExportService provExportService;

    @Transactional
    public AuditTrailExport generateForTenancy(
            final String tenancyId, final Instant from, final Instant to) {
        final List<UUID> subjectIds = repo.findDistinctSubjectIds(tenancyId);
        final List<SubjectAuditTrail> trails = subjectIds.stream()
                .map(sid -> buildSubjectTrail(sid, from, to, tenancyId))
                .toList();
        final VerificationSummary verification = buildVerificationSummary(trails);
        return new AuditTrailExport(tenancyId, from, to, Instant.now(), trails, verification);
    }

    private SubjectAuditTrail buildSubjectTrail(
            final UUID subjectId, final Instant from, final Instant to,
            final String tenancyId) {
        final List<LedgerEntry> entries =
                repo.findBySubjectIdAndTimeRange(subjectId, from, to, tenancyId);
        final List<AuditEntry> auditEntries = entries.stream()
                .map(this::toAuditEntry)
                .toList();
        boolean chainValid;
        String merkleRoot;
        try {
            chainValid = verificationService.verify(subjectId, tenancyId);
            merkleRoot = verificationService.treeRoot(subjectId, tenancyId);
        } catch (final Exception e) {
            chainValid = false;
            merkleRoot = null;
        }
        String provJsonLd;
        try {
            provJsonLd = provExportService.exportSubject(subjectId, tenancyId);
        } catch (final Exception e) {
            provJsonLd = null;
        }
        return new SubjectAuditTrail(subjectId, auditEntries, merkleRoot, chainValid, provJsonLd);
    }
}
```

Verification and PROV-O failures are captured per subject (null values, `chainValid=false`),
not thrown. The audit trail must always be generated — a broken chain in one subject
should not prevent reporting on the others.

---

## Part 4 — Reporting Module (`casehub-ledger-reporting`)

### 4a. Module structure

```
reporting/
├── pom.xml
└── src/main/
    ├── java/io/casehub/ledger/reporting/
    │   ├── LedgerReportingService.java
    │   ├── OutputFormat.java
    │   └── ReportMediaType.java
    └── resources/templates/reports/
        ├── compliance-art12.html
        └── audit-trail.html
```

**Maven coordinates:**
- artifactId: `casehub-ledger-reporting`
- groupId: `io.casehub`
- package: `io.casehub.ledger.reporting`

**Dependencies:**
- `io.casehub:casehub-ledger` (runtime)
- `io.casehub:casehub-platform-api` (PdfGenerator SPI — transitive via ledger-api)
- `io.quarkus:quarkus-qute`

### 4b. `LedgerReportingService`

Uses `OutputFormat` (defined in this module), not `ReportFormat` (the text-only enum
in `ledger-core`). This keeps `ComplianceReport.format()` intact as a pure text formatter.

```java
@ApplicationScoped
public class LedgerReportingService {

    @Inject PdfGenerator pdfGenerator;
    @Inject ObjectMapper objectMapper;
    @Inject Template complianceArt12;
    @Inject Template auditTrail;

    public byte[] renderComplianceReport(ComplianceReport report, OutputFormat format) {
        return switch (format) {
            case JSON    -> report.format(ReportFormat.PLAIN_JSON).getBytes(UTF_8);
            case JSON_LD -> report.format(ReportFormat.JSON_LD).getBytes(UTF_8);
            case CSV     -> report.format(ReportFormat.CSV).getBytes(UTF_8);
            case HTML    -> renderHtml(complianceArt12, report);
            case PDF     -> renderPdf(complianceArt12, report,
                                "EU AI Act Art.12 Compliance Report");
        };
    }

    public byte[] renderAuditTrail(AuditTrailExport export, OutputFormat format) {
        return switch (format) {
            case JSON    -> objectMapper.writeValueAsBytes(export);
            case JSON_LD -> objectMapper.writeValueAsBytes(export);
            case CSV     -> auditTrailToCsv(export);
            case HTML    -> renderHtml(auditTrail, export);
            case PDF     -> renderPdf(auditTrail, export, "Audit Trail Export");
        };
    }

    private byte[] renderHtml(Template template, Object model) {
        return template.data("report", model).render().getBytes(UTF_8);
    }

    private byte[] renderPdf(Template template, Object model, String title) {
        final String html = template.data("report", model).render();
        final PdfOptions opts = new PdfOptions(title, "casehub-ledger",
                Instant.now(), "compliance", PdfAConformance.PDFA_2_B);
        return pdfGenerator.generateFromHtml(html, opts)
                .orElseThrow(() -> new IllegalStateException(
                    "PDF generation unavailable — add platform-pdf to classpath"));
    }

    private byte[] auditTrailToCsv(AuditTrailExport export) {
        // Per-subject entry tables flattened to rows:
        // subjectId,entryId,entryType,occurredAt,actorId,digest,sequenceNumber,chainValid
    }
}
```
```

### 4c. `ReportMediaType`

```java
public final class ReportMediaType {

    private ReportMediaType() {}

    public static OutputFormat fromAcceptHeader(String accept) {
        if (accept == null) return OutputFormat.JSON;
        if (accept.contains("application/pdf")) return OutputFormat.PDF;
        if (accept.contains("text/html")) return OutputFormat.HTML;
        if (accept.contains("text/csv")) return OutputFormat.CSV;
        if (accept.contains("application/ld+json")) return OutputFormat.JSON_LD;
        return OutputFormat.JSON;
    }
}
```

### 4d. Qute Templates

**`compliance-art12.html`** — structured table showing:
- Report header (tenancy, time range, generation timestamp)
- Summary statistics (total decisions, AI-assisted count, human override count)
- Decision table (entry type, occurred at, actor, algorithm, confidence, plan ref, override available)
- Merkle root anchor for tamper evidence

**`audit-trail.html`** — per-subject sections showing:
- Subject ID, entry count, chain validity status, Merkle root
- Entry table (sequence number, entry type, occurred at, actor, digest)
- Verification summary (total subjects, valid/invalid chains)

Templates use clean HTML + CSS suitable for `openhtmltopdf` rendering. No JavaScript.
Must be readable by a non-technical auditor.

---

## Part 5 — Testing Strategy

### Unit tests (pure Java)

| Test | Scope |
|---|---|
| `ComplianceSummaryTest` | Summary computation: counts by type, override count |
| `ReportMediaTypeTest` | Accept header parsing: PDF, HTML, CSV, JSON-LD, JSON, null |

### Integration tests (`@QuarkusTest`)

| Test | Scope |
|---|---|
| `LedgerComplianceReportServiceIT` | `reportForTenancy()`: filters compliance-only entries, summary counts match, empty tenancy returns empty. Existing `reportForActor()`/`reportForSubject()` enriched output. |
| `AuditTrailExportServiceIT` | Tenancy with 2 subjects: per-subject trails, verification per chain, PROV-O included. Empty tenancy → empty list. Broken chain → `chainValid=false`, report still generated. |
| `FindByTimeRangeIT` | Time range filtering, tenancy isolation, boundary inclusion. `findDistinctSubjectIds` uniqueness. |
| `LedgerReportingServiceIT` | JSON rendering valid. HTML rendering produces HTML. PDF rendering produces `%PDF` header (with platform-pdf). CSV audit trail is tabular. `NoOpPdfGenerator` → `IllegalStateException`. |

No Docker required — all tests use H2 in-memory.

---

## File Map

| File | Action |
|---|---|
| `api/.../spi/LedgerEntryRepository.java` | Modify — add 2 methods |
| `ledger-core/.../compliance/ComplianceReport.java` | Modify — add `tenancyId`, `summary` |
| `ledger-core/.../compliance/DecisionRecord.java` | Modify — add `entryType`, `actorId`, `planRef` |
| `ledger-core/.../compliance/ComplianceSummary.java` | Create |
| `ledger-core/.../compliance/AuditTrailExport.java` | Create |
| `ledger-core/.../compliance/SubjectAuditTrail.java` | Create |
| `ledger-core/.../compliance/AuditEntry.java` | Create |
| `ledger-core/.../compliance/VerificationSummary.java` | Create |
| `runtime/.../model/jpa/JpaLedgerEntry.java` | Modify — add 2 `@NamedQuery` |
| `runtime/.../service/LedgerComplianceReportService.java` | Modify — add `reportForTenancy()`, update existing methods |
| `runtime/.../service/AuditTrailExportService.java` | Create |
| `runtime/.../repository/jpa/JpaLedgerEntryRepository.java` | Modify — implement 2 methods |
| `persistence-memory/.../InMemoryLedgerEntryRepository.java` | Modify — implement 2 methods |
| `reporting/pom.xml` | Create |
| `reporting/.../LedgerReportingService.java` | Create |
| `reporting/.../OutputFormat.java` | Create |
| `reporting/.../ReportMediaType.java` | Create |
| `reporting/src/main/resources/templates/reports/compliance-art12.html` | Create |
| `reporting/src/main/resources/templates/reports/audit-trail.html` | Create |
| `pom.xml` | Modify — add `reporting` module |
| `CLAUDE.md` | Modify — add reporting module to project structure |

---

## References

- `platform-api/.../pdf/PdfGenerator.java` — platform PDF SPI (D1)
- `platform-pdf/.../OpenHtmlToPdfGenerator.java` — openhtmltopdf implementation
- `runtime/.../service/LedgerComplianceReportService.java` — existing compliance report service
- `runtime/.../service/LedgerVerificationService.java` — Merkle verification
- `runtime/.../service/LedgerProvExportService.java` — PROV-O export
- `api/.../spi/LedgerEntryRepository.java` — tenant-scoped SPI
- `api/.../model/supplement/ComplianceSupplement.java` — compliance data source
- `docs/specs/2026-04-17-art12-compliance-design.md` — prior Art.12 design (retention + audit queries)
- `docs/protocols/casehub/ledger-entry-named-query.md` — JPQL must use @NamedQuery
- `docs/protocols/casehub/per-subject-table-tenancy.md` — tenancyId in all tenant-scoped queries
- Issue #211 — parent issue
- Issue #212 — Merkle verification bundle (split out, separate concern)
