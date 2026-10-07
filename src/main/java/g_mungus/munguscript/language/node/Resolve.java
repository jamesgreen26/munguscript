package g_mungus.munguscript.language.node;

import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/** Turning what stood in an argument slot into a value of a script type. */
final class Resolve {

    private Resolve() {
    }

    /** What stood in a slot that reads {@code type}'s own argument. */
    static <T> @Nullable T value(ScriptType<T> type, @Nullable Object raw, ScriptContext context) {
        return type.literal().isPresent()
                ? value(type, type.literal().get(), raw, context)
                : value(type, null, null, raw, context);
    }

    private static <T, A> @Nullable T value(ScriptType<T> type, ScriptType.Literal<T, A> literal,
                                            @Nullable Object raw, ScriptContext context) {
        return value(type, literal.argumentClass(), literal::resolve, raw, context);
    }

    /**
     * Either what the argument parsed, which is resolved, or a value already of the type, such as
     * the result of a {@code value_of}, which is passed through.
     *
     * @param argumentClass what the argument parses to, or null when there is no argument to resolve
     */
    static <T, A> @Nullable T value(ScriptType<T> type, @Nullable Class<A> argumentClass,
                                    @Nullable BiFunction<A, ScriptContext, T> resolve,
                                    @Nullable Object raw, ScriptContext context) {
        if (raw == null) {
            return null;
        }
        if (argumentClass != null && resolve != null && argumentClass.isInstance(raw)) {
            return resolve.apply(argumentClass.cast(raw), context);
        }
        if (type.javaClass().isInstance(raw)) {
            return type.javaClass().cast(raw);
        }
        throw mismatch(type, raw);
    }

    static IllegalArgumentException mismatch(ScriptType<?> type, Object raw) {
        return new IllegalArgumentException("Expected " + type.key().path() + ", got " + raw.getClass().getSimpleName());
    }
}
