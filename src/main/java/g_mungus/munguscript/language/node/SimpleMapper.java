package g_mungus.munguscript.language.node;

import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public record SimpleMapper<I, O>(
        String displayName,
        ScriptType<I> inputType,
        ScriptType<O> outputType,
        BiFunction<I, ScriptContext, O> function,
        @Nullable Applicability applicability
) implements ScriptMapper<I, O> {

    @Override
    public O map(I input, ScriptContext context) {
        return function.apply(input, context);
    }

    public SimpleMapper<I, O> withApplicability(@Nullable Applicability applicability) {
        return new SimpleMapper<>(displayName, inputType, outputType, function, applicability);
    }
}
