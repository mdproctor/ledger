package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnricherPipelineCoreTest {

    @Test
    void enrichersRunInPriorityOrder() {
        var order = new ArrayList<String>();
        var enrichers = List.<LedgerEntryEnricher>of(
                new TestEnricher("second", 20, order),
                new TestEnricher("first", 10, order),
                new TestEnricher("third", 30, order)
        );
        var pipeline = new EnricherPipelineCore(enrichers);
        pipeline.enrich(stubEntry());
        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    void defaultPriorityIsMaxValue() {
        var order = new ArrayList<String>();
        var enrichers = List.<LedgerEntryEnricher>of(
                new DefaultPriorityEnricher("default", order),
                new TestEnricher("explicit", 10, order)
        );
        var pipeline = new EnricherPipelineCore(enrichers);
        pipeline.enrich(stubEntry());
        assertThat(order).containsExactly("explicit", "default");
    }

    @Test
    void failingEnricherDoesNotBlockPipeline() {
        var order = new ArrayList<String>();
        var enrichers = List.<LedgerEntryEnricher>of(
                new TestEnricher("before", 10, order),
                new FailingEnricher(20),
                new TestEnricher("after", 30, order)
        );
        var pipeline = new EnricherPipelineCore(enrichers);
        pipeline.enrich(stubEntry());
        assertThat(order).containsExactly("before", "after");
    }

    @Test
    void emptyPipelineIsNoOp() {
        var pipeline = new EnricherPipelineCore(List.of());
        pipeline.enrich(stubEntry());
    }

    record TestEnricher(String name, int prio, List<String> order) implements LedgerEntryEnricher {
        @Override
        public void enrich(LedgerEntry entry) {
            order.add(name);
        }

        @Override
        public int priority() {
            return prio;
        }
    }

    record DefaultPriorityEnricher(String name, List<String> order) implements LedgerEntryEnricher {
        @Override
        public void enrich(LedgerEntry entry) {
            order.add(name);
        }
    }

    record FailingEnricher(int prio) implements LedgerEntryEnricher {
        @Override
        public void enrich(LedgerEntry entry) {
            throw new RuntimeException("boom");
        }

        @Override
        public int priority() {
            return prio;
        }
    }

    private static LedgerEntry stubEntry() {
        return new LedgerEntry() {
            {
                this.id = UUID.randomUUID();
                this.subjectId = UUID.randomUUID();
                this.sequenceNumber = 1;
                this.entryType = LedgerEntryType.EVENT;
                this.actorId = "test-actor";
                this.actorType = ActorType.AGENT;
                this.occurredAt = Instant.now();
            }
        };
    }
}
