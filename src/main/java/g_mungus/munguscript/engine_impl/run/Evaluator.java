package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Evaluates a {@code value_of(...)}, each in a run of its own: the expression is read and checked
 * first, so nothing runs if it cannot give what is wanted, and then run through the tree with a
 * fresh {@link ValueRun} on a copy of the source.
 */
public final class Evaluator<S> {
    private final ScriptHost<S> host;
    private final Supplier<ExpressionReader<S>> reader;

    /**
     * @param reader the reader over the engine's tree. Supplied lazily because the tree's nodes
     *               evaluate through this, so it exists before the tree does.
     */
    public Evaluator(ScriptHost<S> host, Supplier<ExpressionReader<S>> reader) {
        this.host = host;
        this.reader = reader;
    }

    /**
     * The value {@code valueOf} gives, of type {@code target}.
     *
     * @param owner what needs the value, as errors name it: the executor or mapper the argument is for
     * @throws ScriptFault if it cannot be read as a {@code target}, or a step in it fails
     */
    public @Nullable Object evaluate(ValueOf valueOf, TypeKey target, String owner, S source) {
        // Part of whatever live pass the command it is in is.
        RunState state = host.runState(source);
        LivePass pass = LivePass.of(state);
        RunCache cache = RunCache.of(state);
        // The same expression gives the same value within one run, except where a refresh has to record it.
        boolean reuse = cache != null && (pass == null || !pass.refreshing());
        String expression = valueOf.expression();
        if (reuse && cache.has(expression, target)) {
            return cache.get(expression, target);
        }
        ValueRun run = new ValueRun(pass, cache);
        S runSource = host.withRunState(source, run);
        ExpressionReader<S> expressions = reader.get();
        // A command run over and over reads each value_of once, and runs it again from that parse with this run's
        // source.
        Parses parses = cache == null ? null : cache.parses();
        ExpressionReader.Result.Readable<S> readable = parses == null ? read(expressions, valueOf, owner, target, runSource)
                : parses.get(valueOf, owner, target, () -> read(expressions, valueOf, owner, target, runSource));
        ParseResults<S> parse = parses == null ? readable.parse() : Parses.withSource(readable.parse(), runSource);
        Object value = value(expressions, valueOf, readable, parse, run);
        if (reuse) {
            cache.put(expression, target, value);
        }
        return value;
    }

    private @Nullable Object value(ExpressionReader<S> expressions, ValueOf valueOf,
                                   ExpressionReader.Result.Readable<S> readable, ParseResults<S> parse, ValueRun run) {
        try {
            expressions.dispatcher().execute(parse);
        } catch (ScriptFault fault) {
            throw fault.within(valueOf.input());
        } catch (CommandSyntaxException e) {
            throw new ScriptFault(Reasons.of(e), valueOf.input(), valueOf.range(), e);
        }
        try {
            return expressions.conversions().convert(run.value(), readable.type(), readable.as());
        } catch (RuntimeException e) {
            throw new ScriptFault("Could not use value_of(" + valueOf.expression() + ") as " + readable.as().path()
                    + ": " + Reasons.of(e), valueOf.input(), valueOf.range(), e);
        }
    }

    /** Reads {@code valueOf} where {@code owner} wants a {@code target}, failing if it cannot be. */
    private static <S> ExpressionReader.Result.Readable<S> read(ExpressionReader<S> expressions, ValueOf valueOf,
                                                                String owner, TypeKey target, S source) {
        ExpressionReader.Result<S> result = expressions.read(valueOf, owner, target, source);
        if (result instanceof ExpressionReader.Result.Unreadable<S> unreadable) {
            throw new ScriptFault(unreadable.reason(), valueOf.input(), unreadable.range());
        }
        return (ExpressionReader.Result.Readable<S>) result;
    }
}
