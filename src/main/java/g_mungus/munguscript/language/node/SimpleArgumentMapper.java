package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @param argumentScriptType the argument's type, or null for a raw Brigadier argument that is
 *                           passed to the function exactly as it parsed
 * @param argumentClass      what the function reads with {@link ScriptContext#argumentValue}
 */
public record SimpleArgumentMapper<I, O, A>(
        String displayName,
        ScriptType<I> inputType,
        ScriptType<O> outputType,
        @Nullable ScriptType<A> argumentScriptType,
        String argumentHint,
        Function<BuildEnvironment, ArgumentType<?>> argumentFactory,
        Class<A> argumentClass,
        BiFunction<I, ScriptContext, O> function,
        @Nullable Applicability applicability
) implements ScriptArgumentMapper<I, O, A> {

    @Override
    public ArgumentType<?> argumentType(BuildEnvironment environment) {
        return argumentFactory.apply(environment);
    }

    @Override
    public A resolveArgument(Object raw, ScriptContext context) {
        if (argumentScriptType != null) {
            return Resolve.value(argumentScriptType, raw, context);
        }
        if (raw != null && !argumentClass.isInstance(raw)) {
            throw new IllegalArgumentException("Expected argument type " + argumentClass.getSimpleName()
                    + ", got " + raw.getClass().getSimpleName());
        }
        return argumentClass.cast(raw);
    }

    @Override
    public O map(I input, ScriptContext context) {
        return function.apply(input, context);
    }

    public SimpleArgumentMapper<I, O, A> withApplicability(@Nullable Applicability applicability) {
        return new SimpleArgumentMapper<>(displayName, inputType, outputType, argumentScriptType, argumentHint,
                argumentFactory, argumentClass, function, applicability);
    }
}
