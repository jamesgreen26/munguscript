package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import org.jetbrains.annotations.Nullable;

/** An argument node of the engine's tree. Like {@link ScriptLiteralNode}, it compares its redirect too. */
public final class ScriptArgumentNode<S, T> extends ArgumentCommandNode<S, T> {

    public ScriptArgumentNode(String name, ArgumentType<T> type, @Nullable Command<S> command,
                              @Nullable CommandNode<S> redirect, @Nullable RedirectModifier<S> modifier) {
        super(name, type, command, source -> true, redirect, modifier, false, null);
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
