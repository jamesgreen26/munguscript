package g_mungus.munguscript.language.type;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.node.ScriptContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A kind of value scripts pass around.
 *
 * <p>A writable type can appear inline in a script: its {@link Literal} names the argument that
 * reads it, how the argument's result becomes a value, and how a value prints and parses as text.
 * The engine generates {@code as_string}, {@code as_<type>} and {@code ==} mappers for every
 * writable type.
 *
 * <p>An {@linkplain #opaque opaque} type, such as a ship, has no literal form. It only comes out of
 * getters and mappers.
 *
 * <p>A type can be {@linkplain #usableAs usable as} other types: wherever one of those is wanted,
 * a value of this type is converted without a word in the script, and the mappers of those types
 * can follow it. Every type is usable as a string, through its printed form if it is writable.
 *
 * @param <T> the value scripts see
 */
public final class ScriptType<T> {
    private final TypeKey key;
    private final Class<T> javaClass;
    private final String hint;
    private final @Nullable Literal<T, ?> literal;
    private final List<Conversion<T, ?>> conversions;

    private ScriptType(TypeKey key, Class<T> javaClass, String hint, @Nullable Literal<T, ?> literal,
                       List<Conversion<T, ?>> conversions) {
        this.key = key;
        this.javaClass = javaClass;
        this.hint = hint;
        this.literal = literal;
        this.conversions = List.copyOf(conversions);
    }

    /** Starts a writable type whose argument already parses to the value itself, like an int. */
    public static <T> Builder<T, T> writable(TypeKey key, Class<T> javaClass) {
        return new Builder<>(key, javaClass, javaClass, (value, context) -> value);
    }

    /**
     * Starts a writable type whose argument parses to something that still has to be resolved, like
     * coordinates. The resolver gets the run's context because some arguments, like relative
     * coordinates, only mean something at a place.
     */
    public static <T, A> Builder<T, A> writable(TypeKey key, Class<T> javaClass, Class<A> argumentClass,
                                                BiFunction<A, ScriptContext, T> resolve) {
        return new Builder<>(key, javaClass, argumentClass, resolve);
    }

    /** A type scripts can hold but never write, like a ship. */
    public static <T> ScriptType<T> opaque(TypeKey key, Class<T> javaClass) {
        return new ScriptType<>(key, javaClass, key.path(), null, List.of());
    }

    /**
     * This type, also usable wherever a {@code target} is wanted, converted by {@code convert}. The
     * conversion is part of the type, so a client that registers the same type knows it too.
     * Conversions chain: a ship usable as an entity that is usable as a position is usable as a
     * position.
     */
    public <U> ScriptType<T> usableAs(ScriptType<U> target, Function<T, U> convert) {
        if (target.key.equals(key)) {
            throw new IllegalArgumentException("Script type " + key + " cannot be usable as itself");
        }
        List<Conversion<T, ?>> more = new ArrayList<>(conversions);
        more.add(new Conversion<>(target, convert));
        return new ScriptType<>(key, javaClass, hint, literal, more);
    }

    /** The types this one is declared usable as, in the order they were declared. */
    public List<Conversion<T, ?>> conversions() {
        return conversions;
    }

    public TypeKey key() {
        return key;
    }

    public Class<T> javaClass() {
        return javaClass;
    }

    /** What a script should write here, shown as a placeholder, e.g. "coordinates". */
    public String hint() {
        return hint;
    }

    /** How the type is written in a script, or empty if it is opaque. */
    public Optional<Literal<T, ?>> literal() {
        return Optional.ofNullable(literal);
    }

    /** How the type is written in a script, or an exception if it is opaque. */
    public Literal<T, ?> requireLiteral() {
        if (literal == null) {
            throw new UnsupportedOperationException("Script type " + key + " cannot be written in a script");
        }
        return literal;
    }

    @Override
    public String toString() {
        return "ScriptType[" + key + "]";
    }

    /** That a value of one type can be used as a {@code target}, and how it is turned into one. */
    public record Conversion<T, U>(ScriptType<U> target, Function<T, U> convert) {
    }

    /**
     * How a writable type is written in a script.
     *
     * @param <A> what the argument parses to before it is resolved, e.g. unresolved coordinates
     */
    public static final class Literal<T, A> {
        private final Function<BuildEnvironment, ArgumentType<A>> argumentType;
        private final Class<A> argumentClass;
        private final BiFunction<A, ScriptContext, T> resolve;
        private final Function<T, String> print;
        private final Function<String, T> parse;

        private Literal(Function<BuildEnvironment, ArgumentType<A>> argumentType, Class<A> argumentClass,
                        BiFunction<A, ScriptContext, T> resolve, Function<T, String> print,
                        Function<String, T> parse) {
            this.argumentType = argumentType;
            this.argumentClass = argumentClass;
            this.resolve = resolve;
            this.print = print;
            this.parse = parse;
        }

        /** A fresh argument type for one slot in the tree. */
        public ArgumentType<A> argumentType(BuildEnvironment environment) {
            return argumentType.apply(environment);
        }

        public Class<A> argumentClass() {
            return argumentClass;
        }

        /** Turns what the argument parsed into a value. */
        public T resolve(A argument, ScriptContext context) {
            return resolve.apply(argument, context);
        }

        /** The value as scripts print it. {@link #parse} reads this form back. */
        public String print(T value) {
            return print.apply(value);
        }

        /** Reads a value from its printed form. Throws {@link IllegalArgumentException} if it is not one. */
        public T parse(String text) {
            return parse.apply(text);
        }
    }

    public static final class Builder<T, A> {
        private final TypeKey key;
        private final Class<T> javaClass;
        private final Class<A> argumentClass;
        private final BiFunction<A, ScriptContext, T> resolve;
        private String hint;
        private @Nullable Function<BuildEnvironment, ArgumentType<A>> argumentType;
        private Function<T, String> print = String::valueOf;
        private @Nullable Function<String, T> parse;

        private Builder(TypeKey key, Class<T> javaClass, Class<A> argumentClass,
                        BiFunction<A, ScriptContext, T> resolve) {
            this.key = key;
            this.javaClass = javaClass;
            this.argumentClass = argumentClass;
            this.resolve = resolve;
            this.hint = key.path();
        }

        /** The placeholder shown where a script should write one of these. Defaults to the key's path. */
        public Builder<T, A> hint(String hint) {
            this.hint = hint;
            return this;
        }

        public Builder<T, A> argument(ArgumentType<A> argumentType) {
            this.argumentType = environment -> argumentType;
            return this;
        }

        /** For argument types that need the host's build environment, such as its registries. */
        public Builder<T, A> argumentFactory(Function<BuildEnvironment, ArgumentType<A>> argumentType) {
            this.argumentType = argumentType;
            return this;
        }

        /** How a value prints. Defaults to {@link String#valueOf}. */
        public Builder<T, A> print(Function<T, String> print) {
            this.print = print;
            return this;
        }

        /** How a value is read back from what {@link #print} gives. */
        public Builder<T, A> parse(Function<String, T> parse) {
            this.parse = parse;
            return this;
        }

        public ScriptType<T> build() {
            if (argumentType == null || parse == null) {
                throw new IllegalStateException("Script type " + key + " needs an argument type and parse;"
                        + " use ScriptType.opaque for a type scripts cannot write");
            }
            return new ScriptType<>(key, javaClass, hint, new Literal<>(argumentType, argumentClass, resolve, print, parse),
                    List.of());
        }
    }
}
