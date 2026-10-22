package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@code value_of}s of one {@link g_mungus.munguscript.engine.LiveLine}, as read, kept from pass to pass: each is
 * read once and run again from its parse with each pass's source, rather than read again, with every
 * {@code value_of} nested in it, each time it runs. A line runs on whichever thread its host steps it on, so this may
 * be read from several at once.
 */
public final class Parses {
    private record Key(ValueOf valueOf, String owner, TypeKey target) {
    }

    private final Map<Key, ExpressionReader.Result.Readable<?>> readable = new ConcurrentHashMap<>();

    /**
     * {@code valueOf} as read where {@code owner} wants a {@code target}, reading it with {@code read} the first time.
     * One that cannot be read is not kept, so it fails again, the same way, each time.
     */
    @SuppressWarnings("unchecked")
    <S> ExpressionReader.Result.Readable<S> get(ValueOf valueOf, String owner, TypeKey target,
                                                Supplier<ExpressionReader.Result.Readable<S>> read) {
        Key key = new Key(valueOf, owner, target);
        ExpressionReader.Result.Readable<?> found = readable.get(key);
        if (found == null) {
            found = read.get();
            readable.put(key, found);
        }
        return (ExpressionReader.Result.Readable<S>) found;
    }

    /** {@code parse} again, to run with {@code source}: see {@link #withSource}. */
    public static <S> ParseResults<S> withSource(ParseResults<S> parse, S source) {
        return new ParseResults<>(withSource(parse.getContext(), source), parse.getReader(), parse.getExceptions());
    }

    /**
     * The root context again, with {@code source}. Brigadier only uses the source of a parse's root context; every
     * later stage gets the source its redirect gives, so replacing the root's is enough. Not
     * {@link CommandContextBuilder#copy}, which leaves out the redirect modifier, so a command starting with
     * {@code if} would never start its condition.
     */
    public static <S> CommandContextBuilder<S> withSource(CommandContextBuilder<S> parsed, S source) {
        CommandContextBuilder<S> context = new CommandContextBuilder<>(parsed.getDispatcher(), source,
                parsed.getRootNode(), parsed.getRange().getStart());
        for (ParsedCommandNode<S> node : parsed.getNodes()) {
            context.withNode(node.getNode(), node.getRange());
        }
        parsed.getArguments().forEach(context::withArgument);
        context.withCommand(parsed.getCommand());
        if (parsed.getChild() != null) {
            context.withChild(parsed.getChild());
        }
        return context;
    }
}
