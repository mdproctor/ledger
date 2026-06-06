package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.service.supplement.TestEntry;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@link LedgerEntryRepository#findEventsByActorId(String)}.
 *
 * <p>
 * Verifies that the repository correctly:
 * <ul>
 * <li>Returns only EVENT-type entries for the given actor</li>
 * <li>Excludes COMMAND entries</li>
 * <li>Excludes entries from other actors</li>
 * <li>Returns empty list for unknown actors</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(FindEventsByActorIdIT.Profile.class)
class FindEventsByActorIdIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "find-events-actor-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;

    // ── Returns only EVENT entries for the given actor ────────────────────────

    @Test
    @Transactional
    void findEventsByActorId_returnsOnlyEvents() {
        final String actorId = "agent-events-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();
        final Instant now = Instant.now();

        // Create 2 EVENT entries for the actor
        final TestEntry event1 = new TestEntry();
        event1.subjectId = subjectId;
        event1.entryType = LedgerEntryType.EVENT;
        event1.actorId = actorId;
        event1.actorType = ActorType.AGENT;
        event1.actorRole = "Classifier";
        event1.occurredAt = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        repo.save(event1);

        final TestEntry event2 = new TestEntry();
        event2.subjectId = subjectId;
        event2.entryType = LedgerEntryType.EVENT;
        event2.actorId = actorId;
        event2.actorType = ActorType.AGENT;
        event2.actorRole = "Classifier";
        event2.occurredAt = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        repo.save(event2);

        // Create 1 COMMAND entry for the same actor (should be excluded)
        final TestEntry command = new TestEntry();
        command.subjectId = subjectId;
        command.entryType = LedgerEntryType.COMMAND;
        command.actorId = actorId;
        command.actorType = ActorType.AGENT;
        command.actorRole = "Classifier";
        command.occurredAt = now.truncatedTo(ChronoUnit.MILLIS);
        repo.save(command);

        // Query
        final List<LedgerEntry> events = repo.findEventsByActorId(actorId);

        // Assert only EVENT entries returned
        assertThat(events).hasSize(2);
        assertThat(events).extracting(e -> e.id).containsExactlyInAnyOrder(event1.id, event2.id);
        assertThat(events).allMatch(e -> LedgerEntryType.EVENT.equals(e.entryType));
    }

    // ── Excludes entries from other actors ────────────────────────────────────

    @Test
    @Transactional
    void findEventsByActorId_excludesOtherActors() {
        final String actorId = "agent-target-" + UUID.randomUUID();
        final String otherActorId = "agent-other-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();
        final Instant now = Instant.now();

        // Create 1 EVENT for target actor
        final TestEntry targetEvent = new TestEntry();
        targetEvent.subjectId = subjectId;
        targetEvent.entryType = LedgerEntryType.EVENT;
        targetEvent.actorId = actorId;
        targetEvent.actorType = ActorType.AGENT;
        targetEvent.actorRole = "Classifier";
        targetEvent.occurredAt = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        repo.save(targetEvent);

        // Create 1 EVENT for other actor (should be excluded)
        final TestEntry otherEvent = new TestEntry();
        otherEvent.subjectId = subjectId;
        otherEvent.entryType = LedgerEntryType.EVENT;
        otherEvent.actorId = otherActorId;
        otherEvent.actorType = ActorType.AGENT;
        otherEvent.actorRole = "Classifier";
        otherEvent.occurredAt = now.truncatedTo(ChronoUnit.MILLIS);
        repo.save(otherEvent);

        // Query
        final List<LedgerEntry> events = repo.findEventsByActorId(actorId);

        // Assert only target actor's event returned
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id).isEqualTo(targetEvent.id);
    }

    // ── Returns empty for unknown actor ───────────────────────────────────────

    @Test
    @Transactional
    void findEventsByActorId_unknownActor_returnsEmpty() {
        final String unknownActorId = "agent-unknown-" + UUID.randomUUID();

        // Query without seeding any entries
        final List<LedgerEntry> events = repo.findEventsByActorId(unknownActorId);

        // Assert empty result
        assertThat(events).isEmpty();
    }

    // ── Handles pseudonymisation correctly ────────────────────────────────────

    @Test
    @Transactional
    void findEventsByActorId_handlesPseudonymisation() {
        // This test verifies that the repository correctly calls
        // actorIdentityProvider.tokeniseForQuery() before querying.
        // InternalActorIdentityProvider (the default) tokenises to a UUID
        // stored on actor_identity table, then tokeniseForQuery() looks it up.

        final String actorId = "agent-pseudonym-" + UUID.randomUUID();
        final UUID subjectId = UUID.randomUUID();
        final Instant now = Instant.now();

        // Create 1 EVENT for the actor
        final TestEntry event = new TestEntry();
        event.subjectId = subjectId;
        event.entryType = LedgerEntryType.EVENT;
        event.actorId = actorId;
        event.actorType = ActorType.AGENT;
        event.actorRole = "Classifier";
        event.occurredAt = now.truncatedTo(ChronoUnit.MILLIS);
        repo.save(event);

        // Query using the original actorId (not the token)
        final List<LedgerEntry> events = repo.findEventsByActorId(actorId);

        // Assert the entry is found (repo correctly tokenised the query actorId)
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id).isEqualTo(event.id);
    }
}
