package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;

/**
 * An action. Takes one value of its input type, written as an argument or produced by
 * {@code value_of(...)}, and returns a Brigadier-style result.
 *
 * <p>The argument may differ from the input type: an executor can narrow it (an int from 0 to 15)
 * or read something else and convert it (an enum to its ordinal).
 *
 * @param <I> the value the action receives
 * @param <A> what the argument parses to
 */
public non-sealed interface ScriptExecutor<I, A> extends ScriptNode {

    ScriptType<I> inputType();

    ArgumentType<A> argumentType(BuildEnvironment environment);

    Class<A> argumentClass();

    /** Turns what stood in the argument slot, parsed or evaluated, into the input. */
    I resolveArgument(Object raw, ScriptContext context);

    int execute(I input, ScriptContext context);
}
