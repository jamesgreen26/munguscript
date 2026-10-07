package g_mungus.munguscript.engine_impl.build;

import g_mungus.munguscript.language.node.ScriptExecutor;

import java.util.List;

/**
 * The executors that share a name, in registration order. They are one word in a script, with one
 * argument slot; the variant that runs is chosen by the target (see
 * {@link g_mungus.munguscript.engine_impl.run.Overloads}).
 */
public record ExecutorGroup(String name, List<ScriptExecutor<?, ?>> variants) {

    public ExecutorGroup {
        variants = List.copyOf(variants);
    }

    /** What the argument slot is called, after the first variant's input. */
    public String argumentName() {
        return variants.get(0).inputType().hint();
    }
}
