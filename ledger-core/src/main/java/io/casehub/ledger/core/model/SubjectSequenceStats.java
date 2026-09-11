package io.casehub.ledger.core.model;

import java.util.UUID;

public record SubjectSequenceStats(
        UUID subjectId,
        String tenancyId,
        long count,
        int min,
        int max) {}
