package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/**
 * A {@code value_of(...)} as it stood in an argument slot. The slot checks the expression inside
 * while parsing; it runs when the command runs.
 *
 * <p>Positions are in {@code input}, the text it was parsed from. Expressions are parsed in place
 * (see {@link g_mungus.munguscript.engine_impl.expression.ExpressionReader}), so a nested
 * {@code value_of}'s positions are also positions in the outermost command.
 *
 * @param start      where {@code value_of(} starts
 * @param innerStart where the expression starts, just after the opening parenthesis
 * @param innerEnd   where the expression ends, at the closing parenthesis
 * @param end        just past the closing parenthesis
 */
public record ValueOf(String input, int start, int innerStart, int innerEnd, int end) {

    public static final String OPEN = "value_of(";

    private static final SimpleCommandExceptionType UNCLOSED =
            new SimpleCommandExceptionType(new LiteralMessage("value_of( is missing its closing )"));

    /** An expression on its own, as {@link g_mungus.munguscript.engine.ScriptEngine#evaluate} is given it. */
    public static ValueOf whole(String expression) {
        return new ValueOf(expression, 0, 0, expression.length(), expression.length());
    }

    public static boolean startsAt(String text, int position) {
        return text.startsWith(OPEN, position);
    }

    /**
     * Reads a {@code value_of(...)} at the reader's cursor, up to its matching parenthesis. Quoted
     * strings inside may hold parentheses of their own.
     */
    public static ValueOf read(StringReader reader) throws CommandSyntaxException {
        String text = reader.getString();
        int start = reader.getCursor();
        int innerStart = start + OPEN.length();
        int depth = 1;
        int i = innerStart;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (CommandText.isQuote(c)) {
                i = CommandText.skipQuoted(text, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                reader.setCursor(i + 1);
                return new ValueOf(text, start, innerStart, i, i + 1);
            }
            i++;
        }
        throw UNCLOSED.createWithContext(reader);
    }

    /** The expression inside the parentheses, as written. */
    public String expression() {
        return input.substring(innerStart, innerEnd);
    }

    /** The whole {@code value_of(...)}. */
    public StringRange range() {
        return StringRange.between(start, end);
    }
}
