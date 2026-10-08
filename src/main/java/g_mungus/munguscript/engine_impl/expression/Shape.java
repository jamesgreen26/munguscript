package g_mungus.munguscript.engine_impl.expression;

import com.mojang.brigadier.ParseResults;
import g_mungus.munguscript.engine_impl.argument.ValueOfException;
import g_mungus.munguscript.language.type.TypeKey;

/** How far an expression reads, and what it gives if it reads in full. */
public sealed interface Shape {

    /** It reads in full and gives {@code type}. */
    record Gives<S>(TypeKey type, ParseResults<S> parse) implements Shape {
    }

    /** There is nothing to read. */
    record Empty() implements Shape {
    }

    /** The first word is not a getter. */
    record UnknownStart(String word) implements Shape {
    }

    /** It starts with a {@code literal_of(...)} that no primitive type reads, for {@code reason}. */
    record BadLiteral(String reason) implements Shape {
    }

    /** It ends just after a mapper that needs an argument. */
    record MissingArgument(String mapper) implements Shape {
    }

    /** {@code word} does not read as {@code mapper}'s argument. */
    record BadArgument(String word, String mapper) implements Shape {
    }

    /** A {@code value_of} nested in an argument cannot be used there; its own explanation stands. */
    record Nested(ValueOfException problem) implements Shape {
    }

    /** {@code word} is not a mapper for the value it follows. */
    record CannotFollow(String word, TypeKey type) implements Shape {
    }
}
