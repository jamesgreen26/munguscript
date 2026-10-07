package g_mungus.munguscript.engine.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Collection;
import java.util.Optional;

/**
 * Taking the engine's own argument types apart and putting them back together, for a host that
 * sends its command tree to a client. Reached through
 * {@link g_mungus.munguscript.engine.MungusScript#arguments()}.
 *
 * <p>On the server, the host {@linkplain #describe describes} each argument type in the grafted
 * tree; those that are not the engine's it sends as usual. On the client, one {@link Rebuild}
 * rebuilds them all and then makes the view they read {@code value_of(...)} through:
 *
 * <pre>{@code
 * ScriptArguments.Rebuild rebuild = MungusScript.arguments().rebuild();
 * // while decoding the packet, for each of the engine's argument types:
 * ArgumentType<?> type = rebuild.argument(description);
 * // once the tree is decoded:
 * ScriptView<S> view = rebuild.view(host, types, restrictions, graftedUnder);
 * }</pre>
 */
public interface ScriptArguments {

    /** What {@code type} is made of, or empty if it is not one of the engine's argument types. */
    Optional<ArgumentDescription> describe(ArgumentType<?> type);

    /** Starts rebuilding the engine's argument types in one received tree. */
    Rebuild rebuild();

    /**
     * The engine's argument types rebuilt for one received tree, and the view they belong to. Used
     * once: argument types first, then the view.
     */
    interface Rebuild {

        /**
         * An argument type that parses and suggests as the described one did. It is for parsing and
         * suggesting only: a rebuilt overload no longer knows its executors, so it cannot run. It
         * reads {@code value_of(...)} through the {@linkplain #view view}, and fails if used before
         * the view is made.
         *
         * @throws IllegalStateException if the view has already been made
         */
        ArgumentType<?> argument(ArgumentDescription description);

        /**
         * The view over the decoded tree, as {@link g_mungus.munguscript.engine.MungusScript#view}
         * makes it, with every argument type this rebuilt pointed at it.
         *
         * @throws IllegalArgumentException if an argument type this rebuilt is not under
         *                                  {@code graftedUnder}
         * @throws IllegalStateException    if the view has already been made
         */
        <S> ScriptView<S> view(ScriptViewHost<S> host, Collection<ScriptType<?>> types,
                               Collection<Restriction> restrictions, CommandNode<S> graftedUnder);
    }
}
