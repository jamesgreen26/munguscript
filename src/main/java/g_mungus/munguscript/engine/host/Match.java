package g_mungus.munguscript.engine.host;

/**
 * Whether a node is meant for what the current run is aimed at, as the host's
 * {@link g_mungus.munguscript.engine.ScriptViewHost#match} answers it.
 *
 * <p>This only steers which overload of an executor runs and what is suggested (see
 * {@link Restriction}). A node aimed at
 * the wrong target still runs. Choosing between overloads is the engine's rule: the first
 * {@link #EXPLICIT} one, otherwise the first {@link #UNRESTRICTED} one, otherwise none.
 */
public enum Match {
    /** The node names this target. */
    EXPLICIT,
    /** The node is meant for any target. */
    UNRESTRICTED,
    /** The node is meant for other targets. */
    NONE
}
