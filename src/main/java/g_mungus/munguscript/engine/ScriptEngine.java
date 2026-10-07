package g_mungus.munguscript.engine;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Collection;
import java.util.List;

/**
 * One build of the script tree: the registered types and nodes, and the Brigadier nodes made from
 * them. The host keeps one per server and builds a new one on reload.
 *
 * @param <S> the host's command source
 */
public interface ScriptEngine<S> extends ScriptView<S> {

    Collection<ScriptType<?>> types();

    /**
     * Every registered node, including generated mappers. The host builds its "works with" lists
     * from these.
     */
    Collection<ScriptNode> nodes();

    /**
     * Adds the engine's internal nodes under {@code parent}. They must be part of the dispatcher
     * the host executes with, and the host may send them to clients with the rest of its tree.
     */
    void graft(CommandNode<S> parent);

    /**
     * The nodes meant only for some targets, which suggestions leave out where they do not apply.
     * A host that sends its tree to clients sends these with it.
     */
    List<Restriction> restrictions();

    /**
     * A copy of {@code source} with fresh run state, ready to run a script. The host calls it in
     * the redirect to {@link #scriptRoot()}, after putting its own context on the source.
     */
    S begin(S source);

    /** Evaluates what a script would write as {@code value_of(expression)} where {@code type} is wanted. */
    <T> T evaluate(String expression, ScriptType<T> type, S source);

    /**
     * Describes a failure raised while running {@code executedCommand}, with ranges mapped back
     * through {@code sourceMap} to {@code playerCommand}.
     */
    ScriptFailure describe(Throwable failure, String playerCommand, String executedCommand,
                           SourceMap sourceMap);
}
