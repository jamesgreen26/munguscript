package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.host.RunState;
import org.jetbrains.annotations.Nullable;

/**
 * The state of one script command while it runs: the condition being worked out, and which branch
 * of an {@code if ... else ...} chain has run.
 *
 * <p>Brigadier runs a command in stages, one per redirect, and only the last stage's command runs.
 * So a branch followed by {@code else} is run by the {@code else} node as it passes, and the
 * branches after the one that ran do nothing but return its result.
 */
public final class CommandRun implements RunState {
    private boolean conditional;
    private boolean negated;
    private @Nullable Object conditionValue;
    private boolean branchRan;
    private int result;

    /** {@code if} or {@code unless}: the branch now runs only if the condition that follows holds. */
    void startCondition(boolean negated) {
        this.conditional = true;
        this.negated = negated;
        this.conditionValue = null;
    }

    /** Whether conditions still need working out: not once a branch has run. */
    boolean evaluatesConditions() {
        return !branchRan;
    }

    @Nullable Object conditionValue() {
        return conditionValue;
    }

    void setConditionValue(@Nullable Object value) {
        this.conditionValue = value;
    }

    /**
     * Runs the current branch's executor if this is the branch to run. Once one branch has run, every
     * later one gives its result instead.
     */
    int runBranch(Branch executor) throws CommandSyntaxException {
        if (branchRan) {
            return result;
        }
        if (conditional && Boolean.TRUE.equals(conditionValue) == negated) {
            return 0;
        }
        result = executor.run();
        branchRan = true;
        return result;
    }

    /** {@code else}: what follows is a new branch, unconditional unless it starts with a condition. */
    void startElse() {
        conditional = false;
        negated = false;
        conditionValue = null;
    }

    @FunctionalInterface
    interface Branch {
        int run() throws CommandSyntaxException;
    }
}
