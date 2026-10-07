package g_mungus.munguscript.engine_impl.failure;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.engine_impl.argument.CommandText;
import g_mungus.munguscript.engine_impl.argument.ValueOfException;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.run.Reasons;
import g_mungus.munguscript.engine_impl.run.ScriptFault;
import org.jetbrains.annotations.Nullable;

/**
 * Turns whatever a command threw into a {@link ScriptFailure}.
 *
 * <ul>
 *     <li>A {@link ScriptFault} was explained where it happened, and knows where.</li>
 *     <li>A syntax error is explained by parsing the command again. Brigadier's own error only
 *         says "unknown" or "incorrect" when several nodes were tried, and counts positions from
 *         the host's prefix, which the engine never sees.</li>
 *     <li>Anything else came from an executor, which has no position in the command.</li>
 * </ul>
 */
public final class FailureDescriber<S> {
    private final CommandDispatcher<S> commands;

    /**
     * @param commands parses script commands from the script root. It is parsed with no source:
     *                 the engine's nodes are usable by every source, and argument types never read it.
     */
    public FailureDescriber(CommandDispatcher<S> commands) {
        this.commands = commands;
    }

    public ScriptFailure describe(Throwable failure, String playerCommand, String executedCommand,
                                  SourceMap sourceMap) {
        Located located;
        if (failure instanceof ScriptFault fault) {
            located = new Located(fault.reason(), fault.rangeIn(executedCommand).orElse(null));
        } else if (failure instanceof CommandSyntaxException syntax) {
            located = syntaxError(syntax, executedCommand);
        } else {
            located = new Located(Reasons.of(failure), null);
        }
        StringRange range = located.range() == null ? null : sourceMap.toOriginal(located.range());
        return new ScriptFailure(located.reason(), playerCommand, executedCommand, range);
    }

    private Located syntaxError(CommandSyntaxException failure, String command) {
        ParseResults<S> parse = commands.parse(command, null);
        ImmutableStringReader reader = parse.getReader();
        if (!parse.getExceptions().isEmpty()) {
            CommandSyntaxException cause = parse.getExceptions().values().iterator().next();
            // A value_of that cannot stand where it is: point at all of it, not just its first word.
            StringRange range = cause instanceof ValueOfException valueOf ? valueOf.range()
                    : CommandText.wordAt(command, cause.getCursor());
            return new Located(Reasons.of(cause), range);
        }
        if (reader.canRead()) {
            StringRange word = CommandText.wordAt(command, reader.getCursor());
            String text = word.get(command);
            return ExpressionReader.lastNode(parse.getContext()) == null
                    ? new Located("Unknown command '" + text + "'", word)
                    : new Located("'" + text + "' is not valid here", word);
        }
        if (!completes(parse.getContext())) {
            return new Located("The command is incomplete", null);
        }
        // It parses now; whatever failed did so for a reason of its own.
        return new Located(Reasons.of(failure), null);
    }

    private static boolean completes(CommandContextBuilder<?> context) {
        CommandContextBuilder<?> last = context;
        while (last.getChild() != null) {
            last = last.getChild();
        }
        return last.getCommand() != null;
    }

    private record Located(String reason, @Nullable StringRange range) {
    }
}
