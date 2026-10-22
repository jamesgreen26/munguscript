package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.Nullable;

/** Reading command text the way Brigadier does: words end at spaces, and quoted strings are skipped whole. */
public final class CommandText {

    private CommandText() {
    }

    /**
     * Where the quoted string starting at {@code start} ends (just past its closing quote), or the
     * end of the text if it is not closed. Backslashes escape, as in Brigadier's quoted strings.
     */
    public static int skipQuoted(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            }
        }
        return text.length();
    }

    /**
     * Where the parenthesis that closes one opened just before {@code innerStart} is, or -1 if it is
     * not closed. Quoted strings inside may hold parentheses of their own.
     */
    public static int closingParenthesis(String text, int innerStart) {
        int depth = 1;
        int i = innerStart;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (isQuote(c)) {
                i = skipQuoted(text, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * What {@code literal} reads at the cursor, except that a quoted string of a quotable
     * {@link StringArgumentType} may also hold {@code \n}, a line break, which Brigadier refuses as an
     * escape. Script text never holds a real line break inside a command, so this is the only way to
     * write one; {@link LiteralOfArgument#write} writes it back the same way.
     */
    public static Object parseLiteral(ArgumentType<?> literal, StringReader reader) throws CommandSyntaxException {
        if (literal instanceof StringArgumentType string && string.getType() == StringArgumentType.StringType.QUOTABLE_PHRASE
                && reader.canRead() && isQuote(reader.peek())) {
            return readQuoted(reader);
        }
        if (literal instanceof IntegerArgumentType && hasPoint(reader)) {
            // As Brigadier fails it, without Integer.parseInt throwing and filling in a stack trace first: a
            // literal_of tries every double as an int before it reads it as one.
            int start = reader.getCursor();
            String number = number(reader);
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, number);
        }
        return literal.parse(reader);
    }

    /** Whether the number at the cursor, as Brigadier reads one, has a decimal point, so is no int. */
    private static boolean hasPoint(StringReader reader) {
        int start = reader.getCursor();
        boolean point = number(reader).indexOf('.') >= 0;
        reader.setCursor(start);
        return point;
    }

    /** The number at the cursor, as far as {@link StringReader#readInt} reads one, leaving the cursor after it. */
    private static String number(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && StringReader.isAllowedNumber(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    /** Brigadier's {@link StringReader#readQuotedString}, also reading {@code \n} as a line break. */
    private static String readQuoted(StringReader reader) throws CommandSyntaxException {
        char quote = reader.read();
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        while (reader.canRead()) {
            char c = reader.read();
            if (escaped) {
                if (c == quote || c == '\\') {
                    result.append(c);
                } else if (c == 'n') {
                    result.append('\n');
                } else {
                    reader.setCursor(reader.getCursor() - 1);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidEscape()
                            .createWithContext(reader, String.valueOf(c));
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedEndOfQuote().createWithContext(reader);
    }

    public static boolean isQuote(char c) {
        return StringReader.isQuotedStringStart(c);
    }

    /** Where the word starting at {@code start} ends. */
    public static int wordEnd(String text, int start) {
        int end = start;
        while (end < text.length() && text.charAt(end) != ' ') {
            end++;
        }
        return end;
    }

    /** The word at {@code start}, or null if there is none. */
    public static @Nullable StringRange wordAt(String text, int start) {
        if (start < 0 || start >= text.length()) {
            return null;
        }
        return StringRange.between(start, Math.max(start + 1, wordEnd(text, start)));
    }

    public static int skipSpaces(String text, int start) {
        int i = start;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }
}
