package g_mungus.munguscript.engine.preprocess;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What a pre-processor can use. On the server this belongs to a run; on the client it belongs to
 * the open editor and has no host context.
 *
 * @param probe       checks whether text reads as a value of a type, e.g. before expanding an alias
 *                    into an {@code if}
 * @param hostContext the host's context for the run behind this, or null when there is no run
 */
public record PreProcessContext(ExpressionProbe probe, @Nullable Object hostContext) {

    /** The host's context, when there is a run behind this and it is of {@code type}. */
    public <H> Optional<H> host(Class<H> type) {
        return type.isInstance(hostContext) ? Optional.of(type.cast(hostContext)) : Optional.empty();
    }
}
