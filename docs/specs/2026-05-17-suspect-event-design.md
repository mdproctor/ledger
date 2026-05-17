# AgentSignatureSuspectEvent — Design Spec
**Issue:** casehubio/ledger#83
**Epic:** epic-suspect-event
**Date:** 2026-05-17
**Status:** Approved for implementation

---

## Problem

`LedgerVerificationService.verifyAgentSignature()` can return `SUSPECT` — cryptographically valid signature, key subsequently reported `COMPROMISED`. There is no real-time notification mechanism. Consumers who verify entries reactively see SUSPECT, but proactive alerting requires polling. An audit ledger that detects compromise silently is not fulfilling its safety purpose.

---

## Design

### `AgentSignatureSuspectEvent` record

Plain CDI event record, same pattern as `LedgerGapDetected`. No annotations on the record.

```java
package io.casehub.ledger.runtime.service;

public record AgentSignatureSuspectEvent(
        UUID entryId,
        String actorId,
        String keyRef,
        Instant occurredAt,
        Instant effectiveSince) {}
```

- `entryId` — the entry whose signature is SUSPECT
- `actorId` — the actor who signed the entry
- `keyRef` — the key generation that was reported COMPROMISED
- `occurredAt` — when the entry was signed (entry timestamp)
- `effectiveSince` — the earliest matching compromise window's `effectiveSince`; "compromised since when"

**No deduplication** — fires on every call that returns SUSPECT. Consumers deduplicate if needed.

---

### `LedgerVerificationService` — sync path update

Inject `Event<AgentSignatureSuspectEvent>`. After SUSPECT is determined, fire synchronously before returning:

```java
@Inject
Event<AgentSignatureSuspectEvent> suspectEvent;
```

In `verifyAgentSignature()`, replace `return VerificationResult.SUSPECT` with:

```java
final Instant effectiveSince = windows.stream()
        .map(CompromisedWindow::effectiveSince)
        .min(Instant::compareTo)
        .orElseThrow();
suspectEvent.fire(new AgentSignatureSuspectEvent(
        entryId, entry.actorId, entry.agentKeyRef,
        entry.occurredAt, effectiveSince));
return VerificationResult.SUSPECT;
```

---

### `LedgerVerificationService` — new reactive method

```java
public Uni<VerificationResult> verifyAgentSignatureAsync(UUID entryId)
```

Uses `ReactiveLedgerEntryRepository` to load the entry. Calls `KeyRotationService.compromisedWindows()` via a blocking bridge (`Uni.createFrom().item(...)`) — acceptable pending consumer feedback driving #86 (reactive `KeyRotationService`). Fires `suspectEvent.fireAsync()` on SUSPECT.

```java
@Inject
ReactiveLedgerEntryRepository reactiveLedgerRepo;

@Inject
Event<AgentSignatureSuspectEvent> suspectEvent;

public Uni<VerificationResult> verifyAgentSignatureAsync(final UUID entryId) {
    return reactiveLedgerRepo.findEntryById(entryId)
            .onItem().ifNull().failWith(
                    () -> new IllegalArgumentException("Entry not found: " + entryId))
            .onItem().transformToUni(entry -> {
                if (entry.agentSignature == null)
                    return Uni.createFrom().item(VerificationResult.UNSIGNED);
                if (entry.agentPublicKey == null) {
                    LOG.warnf("Entry %s has agentSignature but no agentPublicKey", entryId);
                    return Uni.createFrom().item(VerificationResult.INVALID);
                }
                return Uni.createFrom().item(() -> verifyCryptographic(entry))
                        .onItem().transformToUni(result -> {
                            if (result != VerificationResult.VALID)
                                return Uni.createFrom().item(result);
                            return checkCompromiseAsync(entryId, entry);
                        });
            });
}
```

Private helpers:
- `verifyCryptographic(LedgerEntry)` — extracted from the sync path (Ed25519 verify), returns `VALID` or `INVALID`
- `checkCompromiseAsync(UUID, LedgerEntry)` — calls blocking `compromisedWindows` via `Uni.createFrom().item(...)`, fires `suspectEvent.fireAsync()` if SUSPECT

---

### Consumer pattern (Javadoc example)

```java
// Sync consumer
void onSuspect(@Observes AgentSignatureSuspectEvent event) {
    LOG.errorf("SUSPECT entry %s — actor %s key %s compromised since %s",
            event.entryId(), event.actorId(), event.keyRef(), event.effectiveSince());
}

// Async consumer (non-blocking)
CompletionStage<Void> onSuspectAsync(@ObservesAsync AgentSignatureSuspectEvent event) {
    return alertingService.send(event.entryId(), event.actorId());
}
```

---

## What's out of scope

| Concern | Tracked |
|---------|---------|
| Full reactive `KeyRotationService` variants | #86 |
| Deduplication / rate-limiting on repeated SUSPECT events | Consumer responsibility |
| REST/MCP endpoint to expose `verifyAgentSignatureAsync` | Consumer responsibility |

---

## Testing strategy

**Unit — `AgentSignatureSuspectEventTest`**
- `AgentSignatureSuspectEvent` is a record — test field accessor correctness

**Unit — `LedgerVerificationServiceTest` additions**
- Sync: SUSPECT fires event with correct fields (mock `Event`, verify `fire()` called once)
- Sync: VALID does not fire event
- Sync: INVALID does not fire event

**Integration — `SuspectEventIT` (`@QuarkusTest`)**
- Persist signed entry, record COMPROMISED rotation, call `verifyAgentSignature()` → assert event fired with correct `effectiveSince`
- Call `verifyAgentSignatureAsync()` → assert `Uni` completes with SUSPECT; assert `fireAsync()` triggered
- Scheduled rotation → no event fired

**Reactive parity**
- `verifyAgentSignatureAsync` returns `Uni<VerificationResult.VALID>` for a valid, uncompromised entry
- `verifyAgentSignatureAsync` returns `Uni<VerificationResult.INVALID>` for a tampered entry
- `verifyAgentSignatureAsync` returns `Uni<VerificationResult.UNSIGNED>` for an unsigned entry
