package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/** Builds getters, mappers and executors. Each can be restricted afterwards with {@code withApplicability}. */
public final class ScriptNodes {

    private ScriptNodes() {
    }

    public static <O> SimpleGetter<O> getter(String name, ScriptType<O> outputType,
                                             Function<ScriptContext, O> function) {
        return new SimpleGetter<>(name, outputType, function, null);
    }

    public static <I, O> SimpleMapper<I, O> mapper(String name, ScriptType<I> inputType, ScriptType<O> outputType,
                                                   BiFunction<I, ScriptContext, O> function) {
        return new SimpleMapper<>(name, inputType, outputType, function, null);
    }

    /** Takes an argument of the given type, shown with the type's own hint. Accepts {@code value_of}. */
    public static <I, O, A> SimpleArgumentMapper<I, O, A> argumentMapper(
            String name, ScriptType<I> inputType, ScriptType<O> outputType, ScriptType<A> argumentType,
            BiFunction<I, ScriptContext, O> function) {
        return argumentMapper(name, inputType, outputType, argumentType.hint(), argumentType, function);
    }

    /** Takes an argument of the given type, shown with a hint of its own, e.g. "index" for an int. */
    public static <I, O, A> SimpleArgumentMapper<I, O, A> argumentMapper(
            String name, ScriptType<I> inputType, ScriptType<O> outputType, String hint,
            ScriptType<A> argumentType, BiFunction<I, ScriptContext, O> function) {
        return new SimpleArgumentMapper<>(name, inputType, outputType, argumentType, hint,
                environment -> argumentType.requireLiteral().argumentType(environment), argumentType.javaClass(),
                function, null);
    }

    /** Takes a raw Brigadier argument, passed to the function exactly as it parsed. No {@code value_of}. */
    public static <I, O, A> SimpleArgumentMapper<I, O, A> rawArgumentMapper(
            String name, ScriptType<I> inputType, ScriptType<O> outputType, String hint,
            ArgumentType<A> argumentType, Class<A> argumentClass, BiFunction<I, ScriptContext, O> function) {
        return new SimpleArgumentMapper<>(name, inputType, outputType, null, hint,
                environment -> argumentType, argumentClass, function, null);
    }

    /** Takes the input type's own argument. The type must be writable. */
    public static <I> SimpleExecutor<I, ?> executor(String name, ScriptType<I> inputType,
                                                    ToIntBiFunction<I, ScriptContext> function) {
        return ownArgument(name, inputType, inputType.requireLiteral(), null, function);
    }

    /**
     * Takes the input type's argument narrowed, e.g. an int from 0 to 15. The narrowed argument must
     * parse to what the type's own argument does.
     */
    public static <I> SimpleExecutor<I, ?> executor(String name, ScriptType<I> inputType,
                                                    ArgumentType<?> argumentType,
                                                    ToIntBiFunction<I, ScriptContext> function) {
        return ownArgument(name, inputType, inputType.requireLiteral(), argumentType, function);
    }

    @SuppressWarnings("unchecked")
    private static <I, A> SimpleExecutor<I, A> ownArgument(String name, ScriptType<I> inputType,
                                                           ScriptType.Literal<I, A> literal,
                                                           @Nullable ArgumentType<?> narrowed,
                                                           ToIntBiFunction<I, ScriptContext> function) {
        Function<BuildEnvironment, ArgumentType<A>> argumentFactory = narrowed == null
                ? literal::argumentType
                : environment -> (ArgumentType<A>) narrowed;
        return new SimpleExecutor<>(name, inputType, argumentFactory, literal.argumentClass(), literal::resolve,
                function, null);
    }

    /** Takes an argument of some other kind and maps it to the input type, e.g. an enum to its ordinal. */
    public static <I, A> SimpleExecutor<I, A> executor(String name, ScriptType<I> inputType,
                                                       ArgumentType<A> argumentType, Class<A> argumentClass,
                                                       BiFunction<A, ScriptContext, I> argumentMapper,
                                                       ToIntBiFunction<I, ScriptContext> function) {
        return new SimpleExecutor<>(name, inputType, environment -> argumentType, argumentClass, argumentMapper,
                function, null);
    }
}
