package g_mungus.munguscript.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs a script file with the debug host: {@code run/main.mungus} in the working directory, or the file
 * given as the first argument. Files the script writes go next to it. Exits with 0 if every command
 * ran, 1 if one failed, and 2 if there was no script to run.
 */
public final class ScriptDebugMain {
    public static final String DEFAULT_SCRIPT = "run/main.mungus";

    private ScriptDebugMain() {
    }

    public static void main(String[] args) throws IOException {
        Path script = Path.of(args.length > 0 ? args[0] : DEFAULT_SCRIPT).toAbsolutePath();
        if (!Files.isRegularFile(script)) {
            System.err.println("No script at " + script);
            System.exit(2);
        }
        List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
        boolean ran = new DebugRunner(script.getParent()).run(script.getFileName().toString(), lines, System.err);
        if (ran) {
            System.out.println("Ran " + script.getFileName());
        }
        System.exit(ran ? 0 : 1);
    }
}
