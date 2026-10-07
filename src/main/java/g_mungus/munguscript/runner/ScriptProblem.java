package g_mungus.munguscript.runner;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import org.jetbrains.annotations.Nullable;

/**
 * Something that stopped a script, located in the script: a command that failed, or a problem
 * pre-processing found. Plain data, worded for the person who wrote the script; the host decides
 * how to show it.
 *
 * @param reason  what went wrong
 * @param line    the line's index in the script, from 0, or null if the problem has no line
 * @param text    the text {@code range} points into: the line as written, or the command without
 *                surrounding whitespace once it is being run. Null if the problem has no line.
 * @param range   the part of {@code text} at fault, if it is known
 * @param failure the engine's description, when a command was run and failed; null for a
 *                pre-processing problem
 */
public record ScriptProblem(
        String reason,
        @Nullable Integer line,
        @Nullable String text,
        @Nullable StringRange range,
        @Nullable ScriptFailure failure
) {

    /** The text at fault, or null when the problem has no position. */
    public @Nullable String faultText() {
        return text == null || range == null ? null : range.get(text);
    }
}
