package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
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
