package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.jetbrains.annotations.Nullable;

/**
 * A literal node of the engine's tree. Brigadier's nodes compare equal by name, children and
 * command alone, so anything that deduplicates nodes by equality (a command packet, say) would
 * merge the getter {@code here} of a condition with the {@code here} of a {@code value_of}. These
 * also compare where they redirect to, which keeps such branches apart.
 */
public final class ScriptLiteralNode<S> extends LiteralCommandNode<S> {

    public ScriptLiteralNode(String literal, @Nullable Command<S> command, @Nullable CommandNode<S> redirect,
                             @Nullable RedirectModifier<S> modifier) {
        // Every script node is usable by everyone: restrictions steer suggestions, never parsing.
        super(literal, command, source -> true, redirect, modifier, false);
    }

    /** A node that only gathers children, such as a chain node. */
    public static <S> ScriptLiteralNode<S> place(String name) {
        return new ScriptLiteralNode<>(name, null, null, null);
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
