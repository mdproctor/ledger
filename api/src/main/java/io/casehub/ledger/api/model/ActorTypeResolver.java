package io.casehub.ledger.api.model;

/**
 * Canonical utility for deriving an {@link ActorType} from an actor ID string.
 *
 * <p>
 * The resolution rules, in priority order:
 * <ol>
 * <li>{@code null} or blank → {@link ActorType#SYSTEM} (safe default)</li>
 * <li>{@code "system"} or {@code "system:*"} → {@link ActorType#SYSTEM}</li>
 * <li>{@code "agent:*"} → {@link ActorType#AGENT}</li>
 * <li>Versioned persona format {@code word:word@version} (e.g. {@code "claude:analyst@v1"}) → {@link ActorType#AGENT}</li>
 * <li>A2A protocol role {@code "user"} → {@link ActorType#HUMAN} (human/initiating party in an A2A conversation)</li>
 * <li>A2A protocol role {@code "agent"} → {@link ActorType#AGENT} (AI agent responding in an A2A conversation)</li>
 * <li>Everything else → {@link ActorType#HUMAN}</li>
 * </ol>
 *
 * <p>
 * The two A2A-specific entries above handle protocol role strings, which arrive as bare literals
 * ({@code "user"}, {@code "agent"}) rather than in the namespaced or persona formats used by
 * Qhorus-native actors. Without explicit rules these strings fall through to the catch-all and
 * are classified as {@link ActorType#HUMAN} — correct for {@code "user"} but wrong for
 * {@code "agent"}. The {@code "user"} entry is deliberately redundant with the catch-all to make
 * the A2A mapping explicit and prevent future changes to the catch-all from silently breaking
 * A2A integration.
 *
 * <p>
 * All consumers that derive {@link ActorType} from an actor ID string must use this class
 * to ensure consistent classification across the casehubio ecosystem.
 */
public final class ActorTypeResolver {

    private ActorTypeResolver() {
    }

    /**
     * Resolves the {@link ActorType} for the given actor ID.
     *
     * @param actorId the actor identifier, may be {@code null}
     * @return the resolved {@link ActorType}, never {@code null}
     */
    public static ActorType resolve(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return ActorType.SYSTEM;
        }
        if (actorId.equals("system") || actorId.startsWith("system:")) {
            return ActorType.SYSTEM;
        }
        if (actorId.startsWith("agent:")) {
            return ActorType.AGENT;
        }
        // Versioned persona format: word:word@word — e.g. "claude:analyst@v1"
        if (actorId.matches("[\\w-]+:[\\w-]+@[\\w.]+")) {
            return ActorType.AGENT;
        }
        // A2A sends bare role strings as actor IDs rather than namespaced or persona formats.
        // "user": redundant with catch-all, but explicit to document the intentional A2A mapping.
        if (actorId.equals("user")) {
            return ActorType.HUMAN;
        }
        // "agent": would fall to HUMAN via catch-all — wrong. Explicit rule corrects it.
        if (actorId.equals("agent")) {
            return ActorType.AGENT;
        }
        return ActorType.HUMAN;
    }
}
