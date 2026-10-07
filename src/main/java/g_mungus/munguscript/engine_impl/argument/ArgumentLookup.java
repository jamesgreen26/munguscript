package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * How the engine's argument types reach the view they belong to. Reading a {@code value_of(...)},
 * or suggesting inside it, takes knowing the whole tree, which an argument type built before that
 * tree existed (or rebuilt on a client while it is still being decoded) cannot hold directly. It is
 * pointed at the view once, when the view is made.
 */
public final class ArgumentLookup {
    private volatile @Nullable ArgumentView view;

    /**
     * @throws IllegalStateException if it already points at a view
     */
    public void pointAt(ArgumentView view) {
        if (this.view != null) {
            throw new IllegalStateException("These argument types already belong to a view");
        }
        this.view = view;
    }

    public boolean isSet() {
        return view != null;
    }

    /**
     * The view.
     *
     * @throws IllegalStateException if there is none yet: a client has to make its view before it
     *                               parses or suggests with the argument types it rebuilt
     */
    ArgumentView view() {
        ArgumentView current = view;
        if (current == null) {
            throw new IllegalStateException("The script engine's argument types were used before their view was"
                    + " made; make it with ScriptArguments.Rebuild.view once the tree is decoded");
        }
        return current;
    }

    /** What a view does for the engine's argument types in its tree. */
    public interface ArgumentView {

        /** Suggestions for {@code argument}, whose slot starts where {@code builder} does. */
        <S> CompletableFuture<Suggestions> suggest(ArgumentType<?> argument, CommandContext<S> context,
                                                  SuggestionsBuilder builder);

        /**
         * Reads a {@code value_of(...)} written in {@code argument}'s slot, and says which of
         * {@code targets} it gives.
         *
         * @param targets the types the slot takes, in order of preference
         * @throws ValueOfException if it gives none of them, or does not read at all
         */
        TypeKey check(ArgumentType<?> argument, ValueOf valueOf, List<TypeKey> targets) throws ValueOfException;
    }
}
