package g_mungus.munguscript.engine;

import g_mungus.munguscript.engine.host.RunState;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the engine asks of the program running it: what a view asks, and somewhere to keep
 * run state.
 *
 * <p>Brigadier passes only the command source through a command, so the engine keeps its per-run
 * state there, and the host's context travels with it. A Minecraft host can carry both with
 * {@code CommandSourceStack.withSource(...)}.
 *
 * @param <S> the host's command source
 */
public interface ScriptHost<S> extends ScriptViewHost<S> {

    /** The run state on {@code source}, or null if it is not running a script. */
    @Nullable RunState runState(S source);

    /** A copy of {@code source} carrying {@code state}. */
    S withRunState(S source, RunState state);
}
