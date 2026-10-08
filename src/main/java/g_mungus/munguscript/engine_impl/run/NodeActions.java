package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine_impl.argument.ParsedOverload;
import g_mungus.munguscript.engine_impl.build.ExecutorGroup;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptNode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * What the engine's nodes do when Brigadier runs them: the commands and redirect modifiers the
 * tree is built with.
 *
 * <p>Every one of them reads the run state on the source first, and does nothing without the kind
 * it expects. That is what keeps anything from running outside a run (a host that reaches the
 * script root without {@code begin}, or someone typing the path to an internal node).
 */
public final class NodeActions<S> {
    private final ScriptHost<S> host;
    private final Slots<S> slots;

    public NodeActions(ScriptHost<S> host, Evaluator<S> evaluator) {
        this.host = host;
        this.slots = new Slots<>(evaluator);
    }

    /** A getter or mapper as a step in a chain. */
    public Step<S> step(ScriptNode node, @Nullable String argumentName) {
        return step(node, argumentName, Function.identity());
    }

    /** A mapper in the chain of a type that is usable as its input: {@code convert} turns the value into one. */
    public Step<S> step(ScriptNode node, @Nullable String argumentName,
                        Function<@Nullable Object, @Nullable Object> convert) {
        return new Step<>(node, argumentName, host, slots, convert);
    }

    /** A {@code literal_of(...)} starting a chain, whose argument node is named {@code argumentName}. */
    public Step<S> literalStep(String argumentName) {
        return new Step<>(argumentName, host, slots);
    }

    /** The last step of a {@code value_of}: leaves the result in the run. */
    public Command<S> lastValueStep(Step<S> step) {
        return context -> {
            ValueRun run = state(context, ValueRun.class);
            if (run == null) {
                return 0;
            }
            run.set(step.apply(run.value(), context));
            return 1;
        };
    }

    /** A step of a {@code value_of} that more steps follow. */
    public RedirectModifier<S> valueStep(Step<S> step) {
        return context -> {
            ValueRun run = state(context, ValueRun.class);
            if (run != null) {
                run.set(step.apply(run.value(), context));
            }
            return List.of(context.getSource());
        };
    }

    /** A step of a condition. Skipped once an earlier branch has run. */
    public RedirectModifier<S> conditionStep(Step<S> step) {
        return context -> {
            CommandRun run = state(context, CommandRun.class);
            if (run != null && run.evaluatesConditions()) {
                run.setConditionValue(step.apply(run.conditionValue(), context));
            }
            return List.of(context.getSource());
        };
    }

    /** {@code if} ({@code negated} false) or {@code unless}. */
    public RedirectModifier<S> startCondition(boolean negated) {
        return context -> {
            CommandRun run = state(context, CommandRun.class);
            if (run != null) {
                run.startCondition(negated);
            }
            return List.of(context.getSource());
        };
    }

    /**
     * {@code else} after {@code branch}, the executor of the branch before it. That branch never
     * reaches Brigadier's last stage, so it is run here, before the next branch starts. The tree only
     * has {@code else} after a branch with a condition.
     */
    public RedirectModifier<S> elseBranch(Command<S> branch) {
        return context -> {
            CommandRun run = state(context, CommandRun.class);
            if (run != null) {
                branch.run(context);
                run.startElse();
            }
            return List.of(context.getSource());
        };
    }

    /** An executor's argument: runs the overload meant for the target, if this branch is the one to run. */
    public Command<S> executor(ExecutorGroup group) {
        return context -> {
            ParsedOverload parsed = context.getArgument(group.argumentName(), ParsedOverload.class);
            if (!parsed.runnable()) {
                throw new IllegalStateException("'" + group.name() + "' was rebuilt on a client, where it no longer"
                        + " knows its executors, so it cannot run");
            }
            CommandRun run = state(context, CommandRun.class);
            return run == null ? 0 : run.runBranch(() -> runOverload(group, parsed, context));
        };
    }

    private int runOverload(ExecutorGroup group, ParsedOverload parsed, CommandContext<S> context) {
        S source = context.getSource();
        // Applicability only chooses between overloads: an executor alone under its name runs anywhere.
        int chosen = group.variants().size() == 1 ? 0 : Overloads.choose(parsed.indices(),
                variant -> Overloads.match(host, group.variants().get(variant).applicability(), source));
        // None meant for this target: the command does nothing, which is not a failure.
        return chosen < 0 ? 0 : execute(group.variants().get(chosen), parsed.value(chosen), group.name(), context);
    }

    private <I> int execute(ScriptExecutor<I, ?> executor, Object parsed, String name, CommandContext<S> context) {
        NodeContext nodeContext = NodeContext.of(host, context.getSource());
        Object written = slots.written(parsed, executor.inputType().key(), name, context);
        return executor.execute(executor.resolveArgument(written, nodeContext), nodeContext);
    }

    private <R> @Nullable R state(CommandContext<S> context, Class<R> kind) {
        Object state = host.runState(context.getSource());
        return kind.isInstance(state) ? kind.cast(state) : null;
    }
}
