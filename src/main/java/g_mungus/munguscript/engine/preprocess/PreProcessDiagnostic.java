package g_mungus.munguscript.engine.preprocess;

import com.mojang.brigadier.context.StringRange;
import org.jetbrains.annotations.Nullable;

/**
 * A problem a pre-processor found, worded for players.
 *
 * @param line  the script line, when the problem was found while preparing a script
 * @param range where in the line or command the problem is, if it is known
 */
public record PreProcessDiagnostic(String message, @Nullable Integer line, @Nullable StringRange range) {

    /** A problem with a whole script line, found while preparing. */
    public static PreProcessDiagnostic atLine(int line, String message) {
        return new PreProcessDiagnostic(message, line, null);
    }

    /** A problem in the command being processed. */
    public static PreProcessDiagnostic inCommand(String message, @Nullable StringRange range) {
        return new PreProcessDiagnostic(message, null, range);
    }
}
