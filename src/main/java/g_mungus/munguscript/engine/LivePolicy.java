package g_mungus.munguscript.engine;

import g_mungus.munguscript.language.node.ScriptNode;

/**
 * Which of the host's nodes may run in a {@link LiveLine}'s live runs. The engine's own mappers,
 * and {@code literal_of}, always may: they only compute.
 */
@FunctionalInterface
public interface LivePolicy {

    boolean live(ScriptNode node);
}
