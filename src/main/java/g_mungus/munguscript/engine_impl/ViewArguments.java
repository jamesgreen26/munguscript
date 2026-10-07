package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.argument.ValueOfException;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.suggest.Suggester;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * What a view does for the engine's argument types in its tree: suggests for them, and reads the
 * {@code value_of(...)} written in them while they parse. An argument type cannot see which node it
 * belongs to, so the view looks that up in its tree, to name it in errors ("set_level needs int").
 */
final class ViewArguments<S> implements ArgumentLookup.ArgumentView {
    /** How errors name what needs a value, for an argument type found nowhere in the tree. */
    private static final String UNKNOWN_OWNER = "the command";

    private final Suggester<S> suggester;
    private final ExpressionReader<S> expressions;
    private final Map<ArgumentType<?>, String> owners;

    ViewArguments(ScriptTree<S> tree, Suggester<S> suggester, ExpressionReader<S> expressions) {
        this.suggester = suggester;
        this.expressions = expressions;
        this.owners = tree.argumentOwners();
    }

    @Override
    public <T> CompletableFuture<Suggestions> suggest(ArgumentType<?> argument, CommandContext<T> context,
                                                      SuggestionsBuilder builder) {
        return suggester.forArgument(argument, context, builder);
    }

    @Override
    public TypeKey check(ArgumentType<?> argument, ValueOf valueOf, List<TypeKey> targets) throws ValueOfException {
        // Parsing has no source; expressions only need one to run.
        ExpressionReader.Result<S> result = expressions.read(valueOf, owners.getOrDefault(argument, UNKNOWN_OWNER),
                targets, null);
        if (result instanceof ExpressionReader.Result.Readable<S> readable) {
            return readable.type();
        } else if (result instanceof ExpressionReader.Result.Unreadable<S> unreadable) {
            throw new ValueOfException(unreadable.reason(), valueOf.input(), unreadable.range());
        }
        throw new IllegalStateException("Unknown expression result: " + result);
    }
}
