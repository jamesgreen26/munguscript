package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.ScriptHost;
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
        ValueRun run = new ValueRun();
        S runSource = host.withRunState(source, run);
        ExpressionReader<S> expressions = reader.get();
        ExpressionReader.Result<S> result = expressions.read(valueOf, owner, target, runSource);
        if (result instanceof ExpressionReader.Result.Unreadable<S> unreadable) {
            throw new ScriptFault(unreadable.reason(), valueOf.input(), unreadable.range());
        }
        try {
            expressions.dispatcher().execute(((ExpressionReader.Result.Readable<S>) result).parse());
        } catch (ScriptFault fault) {
            throw fault.within(valueOf.input());
        } catch (CommandSyntaxException e) {
            throw new ScriptFault(Reasons.of(e), valueOf.input(), valueOf.range(), e);
        }
        return run.value();
    }
}
