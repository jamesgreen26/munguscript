package g_mungus.munguscript.language.node;

import org.jetbrains.annotations.Nullable;

/**
 * Something a script command is built from: a getter produces a value, mappers turn it into
 * another, an executor acts on it.
 */
public sealed interface ScriptNode permits ScriptGetter, ScriptMapper, ScriptArgumentMapper, ScriptExecutor {

    /**
     * The word scripts write for this node. Executors may share a name; which of them runs is
     * chosen by their {@link #applicability()} and the target a run is aimed at.
     */
    String displayName();

    /** Which targets the node is meant for, as the host describes them. Null means all of them. */
    @Nullable Applicability applicability();
}
