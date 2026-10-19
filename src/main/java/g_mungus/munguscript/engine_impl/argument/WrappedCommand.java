package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code command(...)} as it stood in a command executor's slot: a whole script command between the
 * brackets. Like a {@link ValueOf}, it is parsed in place, so positions in it are positions in the
 * outermost command.
 *
 * @param start      where {@code command(} starts
 * @param innerStart where the command starts, just after the opening parenthesis
 * @param innerEnd   where the command ends, at the closing parenthesis
 * @param end        just past the closing parenthesis
 */
public record WrappedCommand(String input, int start, int innerStart, int innerEnd, int end) {

    public static final String OPEN = "command(";

    private static final SimpleCommandExceptionType UNCLOSED =
            new SimpleCommandExceptionType(new LiteralMessage("command( is missing its closing )"));
    private static final SimpleCommandExceptionType EXPECTED =
            new SimpleCommandExceptionType(new LiteralMessage("Expected a command, written as command(...)"));

    public static boolean startsAt(String text, int position) {
        return text.startsWith(OPEN, position);
    }

    /** Reads a {@code command(...)} at the reader's cursor, up to its matching parenthesis. */
    public static WrappedCommand read(StringReader reader) throws CommandSyntaxException {
        String text = reader.getString();
        int start = reader.getCursor();
        if (!startsAt(text, start)) {
            throw EXPECTED.createWithContext(reader);
        }
        int innerStart = start + OPEN.length();
        int close = CommandText.closingParenthesis(text, innerStart);
        if (close < 0) {
            throw UNCLOSED.createWithContext(reader);
        }
        reader.setCursor(close + 1);
        return new WrappedCommand(text, start, innerStart, close, close + 1);
    }

    /** The command inside the parentheses, as written. */
    public String command() {
        return input.substring(innerStart, innerEnd).strip();
    }

    /** Parses what is in {@code input} from {@code start} to {@code end} as a command, in place. */
    public static <S> ParseResults<S> parse(CommandDispatcher<S> commands, String input, int start, int end,
                                            @Nullable S source) {
        StringReader reader = new StringReader(input.substring(0, end));
        reader.setCursor(CommandText.skipSpaces(reader.getString(), start));
        return commands.parse(reader, source);
    }

    /** Whether {@code parse} read all of its input, to a node that runs. */
    public static boolean runs(ParseResults<?> parse) {
        if (parse.getReader().canRead()) {
            return false;
        }
        CommandContextBuilder<?> last = parse.getContext();
        while (last.getChild() != null) {
            last = last.getChild();
        }
        return last.getCommand() != null;
    }

    /**
     * Refuses a parse that does not read its whole input to a node that runs, as Brigadier would refuse
     * to execute it.
     */
    public static void requireRuns(ParseResults<?> parse) throws CommandSyntaxException {
        if (runs(parse)) {
            return;
        }
        if (parse.getReader().canRead() && parse.getExceptions().size() == 1) {
            throw parse.getExceptions().values().iterator().next();
        }
        if (parse.getReader().canRead() && !parse.getContext().getRange().isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader());
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
    }
}
