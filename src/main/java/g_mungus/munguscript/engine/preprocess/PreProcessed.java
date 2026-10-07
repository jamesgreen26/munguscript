package g_mungus.munguscript.engine.preprocess;

import java.util.List;

/**
 * One command after pre-processing.
 *
 * @param command     the rewritten command, ready to parse
 * @param sourceMap   maps ranges in {@code command} back to the text before this step
 * @param diagnostics problems with this command, such as an unknown address. A command with
 *                    problems is not run.
 */
public record PreProcessed(String command, SourceMap sourceMap, List<PreProcessDiagnostic> diagnostics) {

    public PreProcessed {
        diagnostics = List.copyOf(diagnostics);
    }

    /** A command this step had nothing to do with. */
    public static PreProcessed unchanged(String command) {
        return new PreProcessed(command, SourceMap.IDENTITY, List.of());
    }

    /** A rewrite with nothing wrong in it. */
    public static PreProcessed of(Rewritten rewritten) {
        return new PreProcessed(rewritten.text(), rewritten.map(), List.of());
    }
}
