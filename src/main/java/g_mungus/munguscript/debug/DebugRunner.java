package g_mungus.munguscript.debug;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.runner.ScriptProblem;
import g_mungus.munguscript.runner.ScriptResult;
import g_mungus.munguscript.runner.ScriptRunner;
import g_mungus.munguscript.runner.SimpleSource;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs a script's commands one after another with the debug nodes, the way a script terminal
 * would, and prints what stopped it. Node functions get the script's directory as their host
 * context, through {@code context.host(Path.class)}.
 */
public final class DebugRunner {
    private final ScriptRunner<SimpleSource> runner;
    private final Path directory;

    /**
     * @param directory where the script's files are read and written
     */
    public DebugRunner(Path directory) {
        this.directory = directory;
        this.runner = ScriptRunner.builder("debug").register(DebugNodes::register).build();
    }

    /**
     * Writes the debug language to {@code file} as a language file, for editors to read scripts by.
     */
    public void writeLanguage(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            runner.writeLanguage(out);
        }
    }

    /**
     * Runs every command in {@code lines}, reporting what stopped it to {@code err}.
     *
     * @param name what to call the script in messages, e.g. its file name
     * @return whether every command ran
     */
    public boolean run(String name, List<String> lines, PrintStream err) {
        ScriptResult result = runner.run(lines, new SimpleSource(directory));
        for (ScriptProblem problem : result.problems()) {
            report(err, name, problem);
        }
        return result.succeeded();
    }

    /** {@code name:line: reason}, then the text with the part at fault underlined. */
    private static void report(PrintStream err, String name, ScriptProblem problem) {
        @Nullable Integer line = problem.line();
        err.println(name + (line == null ? "" : ":" + (line + 1)) + ": " + problem.reason());
        String text = problem.text();
        if (text == null) {
            return;
        }
        err.println("    " + text);
        StringRange range = problem.range();
        if (range != null) {
            int start = Math.min(range.getStart(), text.length());
            int length = Math.max(1, Math.min(range.getEnd(), text.length()) - start);
            err.println("    " + " ".repeat(start) + "^".repeat(length));
        }
    }
}
