package g_mungus.munguscript.language.node;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Set;

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

    /**
     * The {@linkplain ScriptNodes#commandExecutor command executors} this is meant for, as a word of the command
     * in their {@code command(...)}. Inside one, only the executors that name it parse; empty for one that is
     * written at the top of a command only. Outside them, it only steers suggestions, as an applicability does:
     * written at the top of a command, an executor meant for inside one still parses and runs.
     */
    default Set<String> onlyInside() {
        return Set.of();
    }
}
