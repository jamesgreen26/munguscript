package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * How an argument reads, in terms a client without the host's code can parse by: what a
 * {@link PortableHostCodec} writes in place of an argument type. Brigadier's own argument types
 * have a shape each; a host's own can give one by being a {@link PortableArgument}.
 *
 * <p>A shape only says how much text an argument takes and whether it is well formed. A client
 * reading by shape accepts what the host would, as far as the shape can say, and suggests what the
 * shape names.
 */
public sealed interface ArgumentShape {

    record Bool() implements ArgumentShape {
    }

    record IntRange(int min, int max) implements ArgumentShape {
    }

    record LongRange(long min, long max) implements ArgumentShape {
    }

    record FloatRange(float min, float max) implements ArgumentShape {
    }

    record DoubleRange(double min, double max) implements ArgumentShape {
    }

    /** Text, read as {@code kind} says. */
    record Text(Kind kind) implements ArgumentShape {
        public enum Kind {
            /** Brigadier's unquoted word: letters, digits and {@code _-.+}. */
            WORD,
            /** A word, or a quoted string with escapes. */
            QUOTABLE,
            /** Everything to the end of the command. */
            GREEDY,
            /** Any run of characters up to the next space or {@code )}, such as {@code ~1}. */
            TOKEN
        }
    }

    /**
     * One run of characters up to the next space or {@code )}, which all of {@code pattern} must
     * match: a coordinate such as {@code ~1}, or an id such as {@code minecraft:stone}.
     *
     * @param description what it is, for saying what was expected: {@code a coordinate}
     */
    record Matching(String pattern, String description) implements ArgumentShape {
        public Matching {
            Pattern.compile(pattern);
        }
    }

    /** Exactly this word, as in {@code "a" to "b"}. */
    record Word(String word) implements ArgumentShape {
    }

    /** One of these words, which are also what is suggested. */
    record OneOf(List<String> words, boolean ignoreCase) implements ArgumentShape {
        public OneOf {
            words = List.copyOf(words);
        }
    }

    /** These shapes one after another, each separated from the next by one space. */
    record Sequence(List<ArgumentShape> parts) implements ArgumentShape {
        public Sequence {
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("A sequence needs at least one part");
            }
            parts = List.copyOf(parts);
        }
    }

    /**
     * What an argument the host did not describe is read as: a word or a quoted string, so it
     * takes about the right text but accepts what the host may not.
     */
    record Loose() implements ArgumentShape {
    }

    /** {@code type}'s shape: Brigadier's own types as they are, a {@link PortableArgument}'s own, or {@link Loose}. */
    static ArgumentShape of(ArgumentType<?> type) {
        return of(type, unknown -> null);
    }

    /**
     * {@code type}'s shape, as {@link #of(ArgumentType)} gives it, unless {@code shapes} gives one:
     * how a host describes argument types it cannot make {@link PortableArgument}s, such as another
     * library's.
     */
    static ArgumentShape of(ArgumentType<?> type, Function<ArgumentType<?>, @Nullable ArgumentShape> shapes) {
        ArgumentShape given = shapes.apply(type);
        if (given != null) {
            return given;
        } else if (type instanceof PortableArgument portable) {
            return portable.shape();
        } else if (type instanceof BoolArgumentType) {
            return new Bool();
        } else if (type instanceof IntegerArgumentType integer) {
            return new IntRange(integer.getMinimum(), integer.getMaximum());
        } else if (type instanceof LongArgumentType wide) {
            return new LongRange(wide.getMinimum(), wide.getMaximum());
        } else if (type instanceof FloatArgumentType single) {
            return new FloatRange(single.getMinimum(), single.getMaximum());
        } else if (type instanceof DoubleArgumentType real) {
            return new DoubleRange(real.getMinimum(), real.getMaximum());
        } else if (type instanceof StringArgumentType string) {
            return new Text(switch (string.getType()) {
                case SINGLE_WORD -> Text.Kind.WORD;
                case QUOTABLE_PHRASE -> Text.Kind.QUOTABLE;
                case GREEDY_PHRASE -> Text.Kind.GREEDY;
            });
        }
        return new Loose();
    }

    /**
     * An argument type that reads as this shape: Brigadier's own where there is one, so it parses
     * and suggests exactly as the host's did.
     *
     * @param examples what the host's argument gave as its examples
     */
    default ArgumentType<?> argumentType(List<String> examples) {
        if (this instanceof Bool) {
            return BoolArgumentType.bool();
        } else if (this instanceof IntRange range) {
            return IntegerArgumentType.integer(range.min(), range.max());
        } else if (this instanceof LongRange range) {
            return LongArgumentType.longArg(range.min(), range.max());
        } else if (this instanceof FloatRange range) {
            return FloatArgumentType.floatArg(range.min(), range.max());
        } else if (this instanceof DoubleRange range) {
            return DoubleArgumentType.doubleArg(range.min(), range.max());
        } else if (this instanceof Text text && text.kind() != Text.Kind.TOKEN) {
            return switch (text.kind()) {
                case WORD -> StringArgumentType.word();
                case GREEDY -> StringArgumentType.greedyString();
                default -> StringArgumentType.string();
            };
        }
        return new ShapedArgument(this, examples);
    }
}
