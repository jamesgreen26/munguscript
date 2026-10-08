package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code literal_of(...)}: a literal of {@code type} where an expression starts, read by the type's
 * own {@code literal} argument. It parses to what that argument parses to, which for the primitive
 * types is the value itself.
 *
 * <p>Expression roots hold one of these for each primitive type, and Brigadier takes the first that
 * reads, so the order they are added in decides what a literal is. Every one of them fails alike on
 * text no type reads, so whichever failure is reported says the same.
 */
public final class LiteralOfArgument implements ArgumentType<Object> {
    public static final String OPEN = "literal_of(";

    /**
     * The types a {@code literal_of(...)} can give, in the order they are tried: the first whose
     * literal reads the text wins, so {@code 5} is an int, {@code 5.5} a double, and only what no
     * other reads is a string.
     */
    public static final List<ScriptType<?>> PRIMITIVES =
            List.of(BuiltInTypes.INT, BuiltInTypes.DOUBLE, BuiltInTypes.BOOLEAN, BuiltInTypes.STRING);

    private static final SimpleCommandExceptionType NOT_OPENED =
            new SimpleCommandExceptionType(new LiteralMessage("Expected " + OPEN));
    private static final SimpleCommandExceptionType UNCLOSED =
            new SimpleCommandExceptionType(new LiteralMessage(OPEN + " is missing its closing )"));
    private static final DynamicCommandExceptionType NOT_A_LITERAL =
            new DynamicCommandExceptionType(text -> new LiteralMessage(notALiteral((String) text)));

    private final ArgumentType<?> literal;
    private final TypeKey type;

    public LiteralOfArgument(ArgumentType<?> literal, TypeKey type) {
        this.literal = literal;
        this.type = type;
    }

    public ArgumentType<?> literal() {
        return literal;
    }

    public TypeKey type() {
        return type;
    }

    /**
     * Whether {@code failure} only says there was no {@code literal_of(} where an expression started,
     * which is no reason of its own: the word there was meant as something else.
     */
    public static boolean notThere(CommandSyntaxException failure) {
        return failure.getType() == NOT_OPENED;
    }

    public static boolean startsAt(String text, int position) {
        return text.startsWith(OPEN, position);
    }

    @Override
    public Object parse(StringReader reader) throws CommandSyntaxException {
        String text = reader.getString();
        int start = reader.getCursor();
        if (!startsAt(text, start)) {
            throw NOT_OPENED.createWithContext(reader);
        }
        int innerStart = start + OPEN.length();
        int close = closing(text, innerStart);
        if (close < 0) {
            throw UNCLOSED.createWithContext(reader);
        }
        String inner = text.substring(innerStart, close);
        // Read only up to the bracket, so the literal cannot run past it, and must read all of it.
        StringReader within = new StringReader(text.substring(0, close));
        within.setCursor(innerStart);
        Object value;
        try {
            if (inner.isEmpty()) {
                throw NOT_A_LITERAL.createWithContext(reader, inner);
            }
            value = literal.parse(within);
        } catch (CommandSyntaxException e) {
            throw NOT_A_LITERAL.createWithContext(reader, inner);
        }
        if (within.canRead()) {
            throw NOT_A_LITERAL.createWithContext(reader, inner);
        }
        reader.setCursor(close + 1);
        return value;
    }

    /**
     * The primitive type {@code text} is a literal of, as {@code literal_of(...)} would read it: the
     * first in {@link #PRIMITIVES} that reads all of it. Empty if none does.
     */
    public static Optional<TypeKey> literalType(String text) {
        for (ScriptType<?> type : PRIMITIVES) {
            StringReader reader = new StringReader(text);
            try {
                type.requireLiteral().argumentType(BuildEnvironment.EMPTY).parse(reader);
            } catch (CommandSyntaxException e) {
                continue;
            }
            if (!reader.canRead()) {
                return Optional.of(type.key());
            }
        }
        return Optional.empty();
    }

    /**
     * What is wrong with the {@code literal_of(} at {@code start}, if none of the primitive types
     * read it: the same reason {@link #parse} fails with. Null if there is none there.
     */
    public static @Nullable String problem(String text, int start) {
        if (!startsAt(text, start)) {
            return null;
        }
        int innerStart = start + OPEN.length();
        int close = closing(text, innerStart);
        return close < 0 ? OPEN + " is missing its closing )" : notALiteral(text.substring(innerStart, close));
    }

    /** Where the {@code )} closing a literal that starts at {@code from} is, or -1. Brackets in quotes do not count. */
    public static int closing(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (CommandText.isQuote(c)) {
                i = CommandText.skipQuoted(text, i) - 1;
            } else if (c == ')') {
                return i;
            }
        }
        return -1;
    }

    private static String notALiteral(String text) {
        return text.isEmpty()
                ? OPEN + ") is empty: write a number, true or false, or a string"
                : "'" + text + "' is not a literal: write a number, true or false, or a string";
    }

    @Override
    public Collection<String> getExamples() {
        return List.of(OPEN + "\"text\")", OPEN + "5)");
    }

    @Override
    public String toString() {
        return "literal_of(" + literal + ", " + type + ")";
    }
}
