package g_mungus.munguscript.debug;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the debug language to a language file: {@code .mungus/main.mungustree} in the working
 * directory, or the file given as the first argument. Named after {@code run/main.mungus}, it is
 * the file the IntelliJ plugin reads that script by, without being told.
 */
public final class DebugLanguageMain {
    public static final String DEFAULT_FILE = ".mungus/main.mungustree";

    private DebugLanguageMain() {
    }

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : DEFAULT_FILE).toAbsolutePath();
        // The runner's directory is only where scripts' files go; writing the language runs nothing.
        new DebugRunner(file.getParent()).writeLanguage(file);
        System.out.println("Wrote the debug language to " + file);
    }
}
