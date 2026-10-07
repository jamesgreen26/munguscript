package g_mungus.munguscript.language;

import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.ScriptType;

/**
 * Collects types and nodes for one build. A host can hand it out wrapped in an event of its own,
 * so that other code can register nodes too.
 */
public interface ScriptRegistrar {

    /** Registers a type. Fails if its key is taken. */
    <T> ScriptType<T> registerType(ScriptType<T> type);

    /** Registers a node. Every type it uses must be registered by the end of the build. */
    void register(ScriptNode node);
}
