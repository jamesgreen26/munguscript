package g_mungus.munguscript.language.node;

/**
 * What a node's function is given when it runs.
 *
 * <p>The engine knows nothing about where a script runs. That is the host context, an object
 * the host attaches to the command source. A Minecraft host
 * would put the level, position and command source in it, and give its own nodes a richer
 * context built on this one.
 */
public interface ScriptContext {

    /** The host's context for this run, or an exception if it is not of {@code type}. */
    <H> H host(Class<H> type);

    /**
     * The argument written after the running mapper, already resolved.
     *
     * @throws IllegalStateException if the running node takes no argument
     * @throws ClassCastException    if the argument is not of {@code type}
     */
    <A> A argumentValue(Class<A> type);
}
