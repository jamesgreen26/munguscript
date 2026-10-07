package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.argument.ArgumentDescription;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.OverloadedArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Takes the engine's argument types apart on a server, and puts them back together on a client. */
final class ScriptArgumentsImpl implements ScriptArguments {

    @Override
    public Optional<ArgumentDescription> describe(ArgumentType<?> type) {
        if (type instanceof ValueOrLiteralArgument slot) {
            return Optional.of(describe(slot));
        } else if (type instanceof OverloadedArgument overloaded) {
            return Optional.of(new ArgumentDescription.Overloaded(
                    overloaded.variants().stream().map(ScriptArgumentsImpl::describe).toList()));
        }
        return Optional.empty();
    }

    private static ArgumentDescription.ValueOrLiteral describe(ValueOrLiteralArgument slot) {
        return new ArgumentDescription.ValueOrLiteral(slot.literal(), slot.target());
    }

    @Override
    public Rebuild rebuild() {
        return new RebuildImpl();
    }

    /**
     * One received tree's argument types. They share one lookup, which the view is put behind once
     * it is made; until then they suggest only what needs no view.
     */
    private static final class RebuildImpl implements Rebuild {
        private final ArgumentLookup lookup = new ArgumentLookup();
        private final Set<ArgumentType<?>> rebuilt = Collections.newSetFromMap(new IdentityHashMap<>());

        @Override
        public synchronized ArgumentType<?> argument(ArgumentDescription description) {
            if (lookup.isSet()) {
                throw new IllegalStateException("The view over these argument types has already been made");
            }
            ArgumentType<?> argument;
            if (description instanceof ArgumentDescription.ValueOrLiteral slot) {
                argument = slot(slot);
            } else if (description instanceof ArgumentDescription.Overloaded overloaded) {
                argument = new OverloadedArgument(
                        overloaded.variants().stream().map(this::slot).toList(), lookup, false);
            } else {
                throw new IllegalStateException("Unknown argument description: " + description);
            }
            rebuilt.add(argument);
            return argument;
        }

        private ValueOrLiteralArgument slot(ArgumentDescription.ValueOrLiteral description) {
            return new ValueOrLiteralArgument(description.literal(), description.target(), lookup);
        }

        @Override
        public synchronized <S> ScriptView<S> view(ScriptViewHost<S> host, Collection<ScriptType<?>> types,
                                                   Collection<Restriction> restrictions, CommandNode<S> graftedUnder) {
            if (lookup.isSet()) {
                throw new IllegalStateException("The view over these argument types has already been made");
            }
            Set<ArgumentType<?>> missing = Collections.newSetFromMap(new IdentityHashMap<>());
            missing.addAll(rebuilt);
            missing.removeAll(argumentTypesUnder(graftedUnder));
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Rebuilt argument types are not in the tree under '"
                        + graftedUnder.getName() + "': " + missing);
            }
            ViewImpl<S> view = ViewImpl.over(host, types, restrictions, graftedUnder);
            lookup.pointAt(view.arguments());
            return view;
        }

        /** Every argument type in the tree under {@code node}, following redirects too. */
        private static Set<ArgumentType<?>> argumentTypesUnder(CommandNode<?> node) {
            Set<ArgumentType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<CommandNode<?>> pending = new ArrayDeque<>(List.of(node));
            while (!pending.isEmpty()) {
                CommandNode<?> current = pending.pop();
                if (!seen.add(current)) {
                    continue;
                }
                if (current instanceof ArgumentCommandNode<?, ?> argument) {
                    types.add(argument.getType());
                }
                pending.addAll(current.getChildren());
                if (current.getRedirect() != null) {
                    pending.add(current.getRedirect());
                }
            }
            return types;
        }
    }
}
