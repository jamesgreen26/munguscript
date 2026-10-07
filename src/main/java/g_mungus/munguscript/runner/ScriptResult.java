package g_mungus.munguscript.runner;

import java.util.List;

/**
 * What running a script did.
 *
 * @param commands the lines that ran, in order
 * @param problems what stopped the script: every problem preparing it found, or the one command
 *                 that could not be pre-processed or failed. Empty if every line ran.
 */
public record ScriptResult(List<CommandResult> commands, List<ScriptProblem> problems) {

    public ScriptResult {
        commands = List.copyOf(commands);
        problems = List.copyOf(problems);
    }

    /** Whether every line ran. */
    public boolean succeeded() {
        return problems.isEmpty();
    }
}
