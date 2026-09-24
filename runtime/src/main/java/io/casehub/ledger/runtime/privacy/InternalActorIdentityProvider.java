package io.casehub.ledger.runtime.privacy;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.ledger.jpa.ActorIdentity;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Optional;
import java.util.UUID;

/**
 * Built-in token-based actor identity provider backed by the {@code actor_identity} table.
 *
 * <p>
 * Not a CDI bean — constructed by {@link LedgerPrivacyProducer} when
 * {@code casehub.ledger.identity.tokenisation.enabled=true}. The EntityManager
 * it receives is a CDI proxy that resolves to the current transaction's session.
 * The insert path in {@link #tokenise} uses {@code REQUIRES_NEW} so a constraint
 * violation from a concurrent insert does not doom the caller's transaction.
 */
public class InternalActorIdentityProvider implements ActorIdentityProvider {

    private final EntityManager em;

    public InternalActorIdentityProvider(@LedgerPersistenceUnit final EntityManager em) {
        this.em = em;
    }

    /**
     * Returns the existing token for {@code rawActorId}, creating one if absent.
     * {@code null} input returns {@code null}.
     */
    @Override
    public String tokenise(final String rawActorId, final ActorType actorType) {
        if (rawActorId == null) {
            return null;
        }
        if (actorType != null && actorType != ActorType.HUMAN) {
            return rawActorId;
        }
        return em.createNamedQuery("ActorIdentity.findByActorId", ActorIdentity.class)
                 .setParameter("actorId", rawActorId)
                 .getResultStream()
                 .map(a -> a.token)
                 .findFirst()
                 .orElseGet(() -> {
                     try {
                         return QuarkusTransaction.requiringNew().call(() -> {
                             final ActorIdentity identity = new ActorIdentity();
                             identity.token   = UUID.randomUUID().toString();
                             identity.actorId = rawActorId;
                             em.persist(identity);
                             em.flush();
                             return identity.token;
                         });
                     } catch (RuntimeException e) {
                         if (e instanceof ConstraintViolationException
                             || e.getCause() instanceof ConstraintViolationException) {
                             return em.createNamedQuery("ActorIdentity.findByActorId", ActorIdentity.class)
                                      .setParameter("actorId", rawActorId)
                                      .getResultStream()
                                      .map(a -> a.token)
                                      .findFirst()
                                      .orElseThrow(() -> new IllegalStateException(
                                              "Actor identity race: INSERT failed but SELECT still empty for " + rawActorId, e));
                         }
                         throw e;
                     }
                 });
    }

    /**
     * Returns the existing token for {@code rawActorId}, or the raw actorId itself
     * if no mapping exists (SYSTEM/AGENT actors are stored under their raw identity).
     * Returns {@link Optional#empty()} only when input is {@code null}.
     *
     * <p>Callers can distinguish "token found" from "no mapping" by comparing the
     * returned value to the raw input: equal → no mapping; not equal → token found.
     */
    @Override
    public Optional<String> tokeniseForQuery(final String rawActorId) {
        if (rawActorId == null) {
            return Optional.empty();
        }
        return Optional.of(em.createNamedQuery("ActorIdentity.findByActorId", ActorIdentity.class)
                .setParameter("actorId", rawActorId)
                .getResultStream()
                .map(a -> a.token)
                .findFirst()
                .orElse(rawActorId));
    }

    /** Returns the real identity for a token, or empty if the mapping was erased. */
    @Override
    public Optional<String> resolve(final String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(ActorIdentity.class, token))
                .map(a -> a.actorId);
    }

    /** Deletes the token→identity mapping. The token in existing entries becomes unresolvable. */
    @Override
    public void erase(final String rawActorId) {
        if (rawActorId == null) {
            return;
        }
        em.createNamedQuery("ActorIdentity.deleteByActorId")
                .setParameter("actorId", rawActorId)
                .executeUpdate();
        // Bulk JPQL DELETE bypasses the L1 cache — clear it so subsequent
        // em.find() calls hit the database and see the deletion.
        em.clear();
    }
}
