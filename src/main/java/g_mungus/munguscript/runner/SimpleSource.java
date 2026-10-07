package g_mungus.munguscript.runner;

import g_mungus.munguscript.engine.host.RunState;
import org.jetbrains.annotations.Nullable;

/**
 * The command source of a {@link SimpleHost}: the host's context, and the engine's run state while
 * a command runs. Node functions get the context through {@code context.host(...)}.
 *
 * @param context  whatever the host's nodes read from, such as a directory or a world; null for
 *                 nodes that need nothing
 * @param runState the engine's state for the running command; null outside a run
 */
public record SimpleSource(@Nullable Object context, @Nullable RunState runState) {

    /** A source for running scripts against {@code context}. */
    public SimpleSource(@Nullable Object context) {
        this(context, null);
    }
}
