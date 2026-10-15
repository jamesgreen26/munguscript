package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
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
     * What {@code literal} reads at the cursor, except that a quoted string of a quotable
     * {@link StringArgumentType} may also hold {@code \n}, which Brigadier refuses as an escape. It is
     * kept as the two characters {@link BuiltInTypes#ESCAPED_NEWLINE}, the line break every script
     * string uses, so {@code "a\nb"} and {@code "a\\nb"} read alike.
     */
    public static Object parseLiteral(ArgumentType<?> literal, StringReader reader) throws CommandSyntaxException {
        if (literal instanceof StringArgumentType string && string.getType() == StringArgumentType.StringType.QUOTABLE_PHRASE
                && reader.canRead() && isQuote(reader.peek())) {
            return readQuoted(reader);
        }
        return literal.parse(reader);
    }

    /** Brigadier's {@link StringReader#readQuotedString}, also taking {@code \n} as {@link BuiltInTypes#ESCAPED_NEWLINE}. */
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
                    result.append(BuiltInTypes.ESCAPED_NEWLINE);
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
