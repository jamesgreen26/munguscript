package g_mungus.munguscript.language.builtin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.List;
import java.util.Locale;

/**
 * The types every engine has. They are in the {@link #NAMESPACE} namespace, which scripts and
 * errors always write by path: {@code int}, not {@code script:int}.
 */
public final class BuiltInTypes {

    public static final String NAMESPACE = "script";

    /**
     * Lines in a {@link #STRING} are separated by the two characters {@code \n}, not a newline. The
     * built-in line mappers split on it.
     */
    public static final String ESCAPED_NEWLINE = "\\n";

    /** Usable as a {@link #DOUBLE}. */
    public static final ScriptType<Integer> INT = ScriptType.writable(key("int"), Integer.class)
            .argument(IntegerArgumentType.integer())
            .parse(text -> Integer.parseInt(text.trim()))
            .build()
            .usableAs(() -> BuiltInTypes.DOUBLE, Integer::doubleValue);

    /** Usable as an {@link #INT}, rounded to the nearest one. */
    public static final ScriptType<Double> DOUBLE = ScriptType.writable(key("double"), Double.class)
            .argument(DoubleArgumentType.doubleArg())
            .print(BuiltInTypes::formatDouble)
            .parse(text -> Double.parseDouble(text.trim()))
            .build()
            .usableAs(() -> BuiltInTypes.INT, BuiltInTypes::nearestInt);

    public static final ScriptType<String> STRING = ScriptType.writable(key("string"), String.class)
            .argument(StringArgumentType.string())
            .print(text -> text)
            .parse(text -> text)
            .build();

    public static final ScriptType<Boolean> BOOLEAN = ScriptType.writable(key("boolean"), Boolean.class)
            .argument(BoolArgumentType.bool())
            .parse(BuiltInTypes::parseBoolean)
            .build();

    public static final List<ScriptType<?>> ALL = List.of(INT, DOUBLE, STRING, BOOLEAN);

    private BuiltInTypes() {
    }

    public static TypeKey key(String path) {
        return new TypeKey(NAMESPACE, path);
    }

    /** Two decimal places, the way scripts print doubles. */
    public static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** The nearest int, halves rounded up. A double that is no number, or too large for an int, is refused. */
    private static int nearestInt(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("NaN is not a number, so it cannot be used as an int");
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(formatDouble(value) + " is too large to be used as an int");
        }
        return (int) rounded;
    }

    private static boolean parseBoolean(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Expected \"true\" or \"false\", got \"" + text + "\"");
    }
}
