package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A literal node of the engine's tree. Brigadier's nodes compare equal by name, children and
 * command alone, so anything that deduplicates nodes by equality (a command packet, say) would
 * merge the getter {@code here} of a condition with the {@code here} of a {@code value_of}. These
 * also compare where they redirect to, which keeps such branches apart.
 *
 * <p>A word a chain holds only through a conversion is not suggested until it has been started, even
 * by a host's own dispatcher that suggests along the engine's nodes without the engine. Only these
 * nodes know it: a tree rebuilt elsewhere from plain nodes suggests them always, and a view over it
 * tells them apart by {@link NodeNames#converted} instead.
 */
public final class ScriptLiteralNode<S> extends LiteralCommandNode<S> {
    private final boolean onlyOnceStarted;

    public ScriptLiteralNode(String literal, @Nullable Command<S> command, @Nullable CommandNode<S> redirect,
                             @Nullable RedirectModifier<S> modifier) {
        this(literal, command, redirect, modifier, false);
    }

    /**
     * @param onlyOnceStarted whether the word is suggested only once it has been started: for a word
     *                        a chain holds only through a conversion
     */
    public ScriptLiteralNode(String literal, @Nullable Command<S> command, @Nullable CommandNode<S> redirect,
                             @Nullable RedirectModifier<S> modifier, boolean onlyOnceStarted) {
        // Every script node is usable by everyone: restrictions steer suggestions, never parsing.
        super(literal, command, source -> true, redirect, modifier, false);
        this.onlyOnceStarted = onlyOnceStarted;
    }

    /** A node that only gathers children, such as a chain node. */
    public static <S> ScriptLiteralNode<S> place(String name) {
        return new ScriptLiteralNode<>(name, null, null, null);
    }

    /** Whether the word is suggested only once it has been started. */
    public boolean onlyOnceStarted() {
        return onlyOnceStarted;
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return onlyOnceStarted && builder.getRemaining().isEmpty() ? Suggestions.empty()
                : super.listSuggestions(context, builder);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && o instanceof CommandNode<?> other && other.getRedirect() == getRedirect();
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + System.identityHashCode(getRedirect());
    }
}
