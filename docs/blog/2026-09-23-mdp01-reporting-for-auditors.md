---
title: "Reporting for auditors — from data services to offline verification"
date: 2026-09-23
author: mdp
entry_type: note
subtype: diary
series: issue-211-reporting-spi-compliance-report
projects: [casehubio/ledger]
tags: [reporting, compliance, eu-ai-act, merkle, pdf, qute]
---

# Reporting for auditors — from data services to offline verification

The ledger has stored the right data for a while — `ComplianceSupplement` on every
AI-assisted decision, Merkle Mountain Range chains per subject, PROV-O export per
subject. What it couldn't do was answer the auditor's question: "show me everything
in this trial, and let me verify it myself."

That gap split into two problems. First, the data aggregation: existing services
reported per-actor or per-subject, but auditors think in tenancies — a clinical
trial, an AML programme, a trading desk. Second, the format: JSON is fine for
systems, but a human auditor wants something printable, and ideally something
they can run independently without touching the production system.

## The platform already had PDF

The issue originally proposed bringing in OpenPDF. That would've added a transitive
dependency to every consumer of casehub-ledger — which is every CaseHub app. Before
going down that path, I checked what the platform already had. Turns out
`casehub-platform-api` already defines a `PdfGenerator` SPI, and `platform-pdf`
provides `OpenHtmlToPdfGenerator` backed by openhtmltopdf + PDFBox with PDF/A-2B
conformance. The whole thing is already wired as a `@DefaultBean` / `@Alternative`
pattern — `NoOpPdfGenerator` active by default, real implementation opt-in.

So the ledger doesn't need to own PDF at all. It produces HTML (via Qute templates),
hands it to the platform's `PdfGenerator`, and gets bytes back. Consumers who want
actual PDF add `platform-pdf` to their classpath. Everyone else gets a clean error
message.

## Three layers, two modules

The architecture landed as a clean separation: data services in `runtime` (they need
EntityManager), presentation in a new `reporting` module (Qute + PdfGenerator +
content negotiation). The `reporting` module follows the same pattern as `rest/` and
`graphql/` — opt-in plain JAR, not a Quarkus extension.

`LedgerComplianceReportService` gained `reportForTenancy()`. The existing per-actor
and per-subject methods got enriched — `tenancyId` and `ComplianceSummary` on every
report now. `AuditTrailExportService` is new: it iterates all subjects in a tenancy,
runs Merkle verification and PROV-O export per subject, and packages the results.
Verification failures are captured, not thrown — a broken chain in one subject
shouldn't prevent reporting on the rest.

The `reporting` module is deliberately thin: `LedgerReportingService` takes a
report model and an `OutputFormat` (JSON, JSON-LD, CSV, HTML, PDF) and produces
bytes. `ReportMediaType` handles content negotiation from Accept headers. Two
Qute templates produce clean HTML tables suitable for openhtmltopdf rendering.

One subtlety worth noting: we kept `ReportFormat` (the text-only enum in
`ledger-core`) unchanged and introduced `OutputFormat` in the reporting module.
`ComplianceReport.format()` returns `String` — adding PDF to that enum would break
its contract since PDF is `byte[]`. Clean boundary: the record handles text
serialisation, the service handles binary rendering.

## Offline verification

The second issue — #212, split from #211 — added `MerkleVerificationBundleService`.
It generates a self-contained package: every entry's digest, the MMR frontier nodes,
the stored root per subject, a Python verification script, and human-readable
instructions.

The Python script is the interesting part. It replicates the MMR binary-carry
propagation and internal hash algorithm (`SHA-256(0x01 | left | right)` per
RFC 9162). An auditor runs `python3 verify.py bundle.json` and gets pass/fail
per subject chain. Zero external dependencies — just `hashlib`, `json`, `sys`.

The verification is chain-only: it takes the precomputed digests as given and
verifies the tree structure. Full field-level verification — recomputing
`canonicalBytes()` from raw field values — isn't feasible for a generic script
because `domainContentBytes()` varies by consumer subclass. Chain-only still
catches the primary tamper concern: reordering, deletion, or insertion of entries
after hash computation.

## What this opens up

The first consumer will be `casehubio/clinical` (#162) — IND safety reporting and
audit trails for clinical trials. But the infrastructure is domain-agnostic:
AML compliance reporting, trading surveillance audit trails, and any other consumer
can use the same services and templates.

The offline verification bundle is particularly interesting for regulatory contexts
where auditors need to verify independently — they can run the Python script
without access to the production database, on their own hardware, with no
CaseHub dependencies.
