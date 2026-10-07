package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.context.CommandContext;
import g_mungus.munguscript.engine_impl.argument.Placeholder;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

/**
 * What stood in an argument slot, ready for the node's own {@code resolveArgument}: a
 * {@code value_of(...)} is evaluated, a placeholder is refused, and anything else is what the
 * argument parsed.
 */
final class Slots<S> {
    private final Evaluator<S> evaluator;

    Slots(Evaluator<S> evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * @param target the type a {@code value_of} here has to give
     * @param owner  the node the slot belongs to, as errors name it
     */
    @Nullable Object written(@Nullable Object parsed, TypeKey target, String owner, CommandContext<S> context) {
        if (parsed instanceof ValueOf valueOf) {
            return evaluator.evaluate(valueOf, target, owner, context.getSource());
        } else if (parsed instanceof Placeholder placeholder) {
            throw new ScriptFault(
                    "Argument placeholder " + Placeholder.TEXT + " must be replaced before execution",
                    context.getInput(), placeholder.range());
        }
        return parsed;
    }
}
