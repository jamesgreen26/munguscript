package g_mungus.munguscript.engine_impl.preprocess;

import g_mungus.munguscript.engine_impl.argument.CommandText;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.tree.NodeNames;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Where expressions start in command text, found from the text alone: after a leading {@code if}
 * or {@code unless}, after {@code else if} or {@code else unless}, and just inside every
 * {@code value_of(}. Quoted strings are skipped, and words inside a {@code value_of(...)} are not
 * keywords of the command around it.
 */
final class ExpressionStarts {

    private ExpressionStarts() {
    }

    /** In a script command. */
    static List<Integer> inCommand(String command) {
        TreeSet<Integer> starts = new TreeSet<>(insideValueOf(command));
        List<Word> words = topLevelWords(command);
        for (int i = 0; i + 1 < words.size(); i++) {
            boolean leading = i == 0 || words.get(i - 1).is(NodeNames.ELSE);
            if (leading && (words.get(i).is(NodeNames.IF) || words.get(i).is(NodeNames.UNLESS))) {
                starts.add(words.get(i + 1).start());
            }
        }
        return List.copyOf(starts);
    }

    /** In an expression, which itself starts with one. */
    static List<Integer> inExpression(String expression) {
        TreeSet<Integer> starts = new TreeSet<>(insideValueOf(expression));
        starts.add(CommandText.skipSpaces(expression, 0));
        return List.copyOf(starts);
    }

    private static List<Integer> insideValueOf(String text) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (CommandText.isQuote(c)) {
                i = CommandText.skipQuoted(text, i) - 1;
            } else if (ValueOf.startsAt(text, i) && (i == 0 || text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '(')) {
                starts.add(CommandText.skipSpaces(text, i + ValueOf.OPEN.length()));
            }
        }
        return starts;
    }

    /** The command's own words: separated by spaces outside quotes and parentheses. */
    private static List<Word> topLevelWords(String text) {
        List<Word> words = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (c == ' ' && depth == 0) {
                if (start >= 0) {
                    words.add(new Word(text.substring(start, i), start));
                    start = -1;
                }
                continue;
            }
            if (start < 0) {
                start = i;
            }
            if (CommandText.isQuote(c)) {
                i = CommandText.skipQuoted(text, i) - 1;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
        }
        return words;
    }

    private record Word(String text, int start) {
        boolean is(String keyword) {
            return text.equals(keyword);
        }
    }
}
