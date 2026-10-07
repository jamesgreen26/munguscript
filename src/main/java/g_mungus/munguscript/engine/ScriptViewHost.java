package g_mungus.munguscript.engine;

import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;

/**
 * What a view asks of the program it is in: enough to parse and suggest, nothing to run. A client
 * that only has a {@link ScriptView} implements this; a host that runs scripts implements
 * {@link ScriptHost}, which extends it.
 *
 * @param <S> the command source, which need not be able to carry run state
 */
public interface ScriptViewHost<S> {

    /** The host's context for {@code source}, given to node functions through {@link ScriptContext#host}. */
    Object hostContext(S source);

    /**
     * Whether a node is meant for what {@code source} is aimed at. Never called for nodes with no
     * applicability; those are {@link Match#UNRESTRICTED}. See {@link Match} for what the answer
     * steers.
     */
    Match match(Applicability applicability, ScriptContext context);

    /**
     * Types in this namespace are written by path alone: {@code block_pos}, not
     * {@code zps:block_pos}. The engine's own types are always written by path.
     */
    String defaultNamespace();
}
