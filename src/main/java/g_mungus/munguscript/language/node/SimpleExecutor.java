package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * @param argumentMapper turns what the argument parsed into the input
 */
public record SimpleExecutor<I, A>(
        String displayName,
        ScriptType<I> inputType,
        Function<BuildEnvironment, ArgumentType<A>> argumentFactory,
        Class<A> argumentClass,
        BiFunction<A, ScriptContext, I> argumentMapper,
        ToIntBiFunction<I, ScriptContext> function,
        @Nullable Applicability applicability
) implements ScriptExecutor<I, A> {

    @Override
    public ArgumentType<A> argumentType(BuildEnvironment environment) {
        return argumentFactory.apply(environment);
    }

    /**
     * Either what the argument parsed, which goes through the argument mapper, or a value that
     * already is the input type, such as the result of a {@code value_of}.
     */
    @Override
    public I resolveArgument(Object raw, ScriptContext context) {
        return Resolve.value(inputType, argumentClass, argumentMapper, raw, context);
    }

    @Override
    public int execute(I input, ScriptContext context) {
        return function.applyAsInt(input, context);
    }

    /** The same executor, preferred for these targets when another executor shares its name. */
    public SimpleExecutor<I, A> withApplicability(@Nullable Applicability applicability) {
        return new SimpleExecutor<>(displayName, inputType, argumentFactory, argumentClass, argumentMapper, function,
                applicability);
    }
}
