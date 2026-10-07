package g_mungus.munguscript.conformance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.munguscript.engine.argument.ScriptArguments;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A command tree sent to a client the way Minecraft's command packet sends it: node names,
 * argument types and redirects arrive, commands and redirect modifiers do not. The engine's own
 * argument types are described on the server side and rebuilt on the client side with one
 * {@link ScriptArguments.Rebuild}.
 */
final class Transfer {

    private Transfer() {
    }

    /**
     * What the client has once it has decoded a server's command tree.
     *
     * @param dispatcher the client's own dispatcher over the whole tree, as chat suggestions use it
     * @param grafted    the client's copy of the node the engine was grafted under
     */
    record Received<S>(CommandDispatcher<S> dispatcher, CommandNode<S> grafted) {
    }

    /** Sends the whole tree under {@code root}, in which the engine was grafted under {@code grafted}. */
    static <S> Received<S> send(RootCommandNode<S> root, CommandNode<S> grafted, ScriptArguments arguments,
                                ScriptArguments.Rebuild rebuild) {
        return transfer(root, grafted, arguments, rebuild, false);
    }

    /**
     * The same, but keeping every command and redirect modifier: a tree that could run, as if a
     * host ran commands against the argument types it rebuilt.
     */
    static <S> Received<S> sendRunnable(RootCommandNode<S> root, CommandNode<S> grafted, ScriptArguments arguments,
                                        ScriptArguments.Rebuild rebuild) {
        return transfer(root, grafted, arguments, rebuild, true);
    }

    private static <S> Received<S> transfer(RootCommandNode<S> root, CommandNode<S> grafted, ScriptArguments arguments,
                                            ScriptArguments.Rebuild rebuild, boolean runnable) {
        Map<CommandNode<S>, CommandNode<S>> copies = copy(root, new Rebuilding(arguments, rebuild, runnable));
        return new Received<>(new CommandDispatcher<>((RootCommandNode<S>) copies.get(root)), copies.get(grafted));
    }

    private record Rebuilding(ScriptArguments arguments, ScriptArguments.Rebuild rebuild, boolean runnable) {
    }

    private static <S> Map<CommandNode<S>, CommandNode<S>> copy(CommandNode<S> root, Rebuilding rebuilding) {
        Map<CommandNode<S>, CommandNode<S>> copies = new IdentityHashMap<>();
        Set<CommandNode<S>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<CommandNode<S>> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            CommandNode<S> node = pending.pop();
            if (!visited.add(node)) continue;
            received(node, copies, rebuilding);
            pending.addAll(node.getChildren());
            if (node.getRedirect() != null) pending.push(node.getRedirect());
        }
        // Children are added once every node exists, so the tree's cycles survive.
        copies.forEach((original, copy) -> original.getChildren().forEach(child -> copy.addChild(copies.get(child))));
        return copies;
    }

    private static <S> CommandNode<S> received(CommandNode<S> node, Map<CommandNode<S>, CommandNode<S>> copies,
                                               Rebuilding rebuilding) {
        CommandNode<S> existing = copies.get(node);
        if (existing != null) {
            return existing;
        }
        CommandNode<S> redirect = node.getRedirect() == null ? null
                : received(node.getRedirect(), copies, rebuilding);
        CommandNode<S> copy;
        if (node instanceof RootCommandNode<S>) {
            copy = new RootCommandNode<>();
        } else if (node instanceof LiteralCommandNode<S> literal) {
            copy = new LiteralCommandNode<>(literal.getLiteral(),
                    rebuilding.runnable() ? literal.getCommand() : null, source -> true, redirect,
                    rebuilding.runnable() ? literal.getRedirectModifier() : null, literal.isFork());
        } else if (node instanceof ArgumentCommandNode<S, ?> argument) {
            copy = argument(argument, redirect, rebuilding);
        } else {
            throw new IllegalArgumentException("Cannot send " + node);
        }
        copies.put(node, copy);
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <S> CommandNode<S> argument(ArgumentCommandNode<S, ?> argument, CommandNode<S> redirect,
                                               Rebuilding rebuilding) {
        ArgumentType type = rebuilding.arguments().describe(argument.getType())
                .map(description -> (ArgumentType) rebuilding.rebuild().argument(description))
                .orElse(argument.getType());
        return new ArgumentCommandNode<>(argument.getName(), type,
                rebuilding.runnable() ? argument.getCommand() : null, source -> true, redirect,
                rebuilding.runnable() ? argument.getRedirectModifier() : null, argument.isFork(), null);
    }
}
