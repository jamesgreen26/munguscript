package g_mungus.munguscript.language.builtin;

import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.util.List;
import java.util.regex.Pattern;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.DOUBLE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.ESCAPED_NEWLINE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;

/**
 * The mappers every engine has, between the built-in types: comparisons and arithmetic on numbers,
 * rounding, string joining and lines, and boolean logic.
 *
 * <p>A mapper the host registers with the same name and input type replaces the built-in one.
 */
public final class BuiltInMappers {

    private static final Pattern ESCAPED_NEWLINE_PATTERN = Pattern.compile(Pattern.quote(ESCAPED_NEWLINE));

    public static final List<ScriptNode> ALL = List.of(
            ScriptNodes.argumentMapper(">", INT, BOOLEAN, INT,
                    (value, context) -> value > context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("<", INT, BOOLEAN, INT,
                    (value, context) -> value < context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("+", INT, INT, INT,
                    (value, context) -> value + context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("-", INT, INT, INT,
                    (value, context) -> value - context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("&", INT, INT, INT,
                    (value, context) -> value & context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("|", INT, INT, INT,
                    (value, context) -> value | context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("<<", INT, INT, INT,
                    (value, context) -> value << context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper(">>", INT, INT, INT,
                    (value, context) -> value >> context.argumentValue(Integer.class)),
            ScriptNodes.argumentMapper("%", INT, INT, INT,
                    (value, context) -> value % context.argumentValue(Integer.class)),
            // Multiplying or dividing an int gives a double, so "level * 0.5" works.
            ScriptNodes.argumentMapper("*", INT, DOUBLE, DOUBLE,
                    (value, context) -> value * context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("/", INT, DOUBLE, DOUBLE,
                    (value, context) -> value / context.argumentValue(Double.class)),

            ScriptNodes.argumentMapper(">", DOUBLE, BOOLEAN, DOUBLE,
                    (value, context) -> value > context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("<", DOUBLE, BOOLEAN, DOUBLE,
                    (value, context) -> value < context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("+", DOUBLE, DOUBLE, DOUBLE,
                    (value, context) -> value + context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("-", DOUBLE, DOUBLE, DOUBLE,
                    (value, context) -> value - context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("*", DOUBLE, DOUBLE, DOUBLE,
                    (value, context) -> value * context.argumentValue(Double.class)),
            ScriptNodes.argumentMapper("/", DOUBLE, DOUBLE, DOUBLE,
                    (value, context) -> value / context.argumentValue(Double.class)),
            ScriptNodes.mapper("rounded_down", DOUBLE, INT, (value, context) -> (int) Math.floor(value)),
            ScriptNodes.mapper("rounded_up", DOUBLE, INT, (value, context) -> (int) Math.ceil(value)),

            ScriptNodes.argumentMapper("+", STRING, STRING, STRING,
                    (text, context) -> text + context.argumentValue(String.class)),
            ScriptNodes.argumentMapper("<+", STRING, STRING, STRING,
                    (text, context) -> context.argumentValue(String.class) + text),
            ScriptNodes.mapper("lines", STRING, INT, (text, context) -> splitLines(text).length),
            ScriptNodes.argumentMapper("get_line", STRING, STRING, "index", INT,
                    (text, context) -> getLine(text, context.argumentValue(Integer.class))),
            ScriptNodes.argumentMapper("remove_line", STRING, STRING, INT,
                    (text, context) -> removeLine(text, context.argumentValue(Integer.class))),
            ScriptNodes.argumentMapper("split", STRING, STRING, "delimiter", STRING,
                    (text, context) -> split(text, context.argumentValue(String.class))),

            ScriptNodes.argumentMapper("&&", BOOLEAN, BOOLEAN, BOOLEAN,
                    (value, context) -> value && context.argumentValue(Boolean.class)),
            ScriptNodes.argumentMapper("||", BOOLEAN, BOOLEAN, BOOLEAN,
                    (value, context) -> value || context.argumentValue(Boolean.class))
    );

    private BuiltInMappers() {
    }

    private static String[] splitLines(String text) {
        return ESCAPED_NEWLINE_PATTERN.split(text, -1);
    }

    /** The line at a 1-based index, or an empty string if there is none. */
    private static String getLine(String text, int lineNumber) {
        String[] lines = splitLines(text);
        int index = lineNumber - 1;
        return index >= 0 && index < lines.length ? lines[index] : "";
    }

    /** The text without the line at a 1-based index, or unchanged if there is none. */
    private static String removeLine(String text, int lineNumber) {
        String[] lines = splitLines(text);
        int index = lineNumber - 1;
        if (index < 0 || index >= lines.length) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == index) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(ESCAPED_NEWLINE);
            }
            result.append(lines[i]);
        }
        return result.toString();
    }

    /** Every occurrence of the delimiter becomes a line break. */
    private static String split(String text, String delimiter) {
        return delimiter.isEmpty() ? text : text.replace(delimiter, ESCAPED_NEWLINE);
    }
}
