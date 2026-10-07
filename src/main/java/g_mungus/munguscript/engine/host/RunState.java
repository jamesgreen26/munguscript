package g_mungus.munguscript.engine.host;

/**
 * The engine's state for one command while it runs: the value so far, the pending condition,
 * the {@code else} branch. Only the engine reads or creates it; the host only carries it on the
 * command source (see {@link g_mungus.munguscript.engine.ScriptHost#withRunState}).
 */
public interface RunState {
}
