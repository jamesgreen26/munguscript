package g_mungus.munguscript.language.node;

import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public record SimpleGetter<O>(
        String displayName,
        ScriptType<O> outputType,
        Function<ScriptContext, O> function,
        @Nullable Applicability applicability
) implements ScriptGetter<O> {

    @Override
    public O get(ScriptContext context) {
        return function.apply(context);
    }

    /** The same getter, meant for these targets only. */
    public SimpleGetter<O> withApplicability(@Nullable Applicability applicability) {
        return new SimpleGetter<>(displayName, outputType, function, applicability);
    }
}
