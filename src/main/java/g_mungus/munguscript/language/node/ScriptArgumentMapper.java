package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

/**
 * Turns one value into another using an argument written after it, as in {@code + 5}.
 *
 * @param <A> the argument's value once resolved
 */
public non-sealed interface ScriptArgumentMapper<I, O, A> extends ScriptNode {

    ScriptType<I> inputType();

    ScriptType<O> outputType();

    /**
     * The argument's script type. When set, the slot also accepts {@code value_of(...)} and
     * anything a pre-processor puts there for that type. When null, only {@link #argumentType}
     * is accepted.
     */
    @Nullable ScriptType<A> argumentScriptType();

    /** The placeholder shown for the argument. */
    String argumentHint();

    ArgumentType<?> argumentType(BuildEnvironment environment);

    /** Turns what stood in the argument slot, parsed or evaluated, into the argument's value. */
    A resolveArgument(Object raw, ScriptContext context);

    /** Reads the argument through {@link ScriptContext#argumentValue}. */
    O map(I input, ScriptContext context);
}
