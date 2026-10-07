package g_mungus.munguscript.debug;

import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.ESCAPED_NEWLINE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;

/**
 * {@code read_file} and {@code write_file}, on {@code output.txt} in the script's directory.
 *
 * <p>Scripts mark line breaks in a string with the two characters {@code \n}, which is what the
 * built-in line mappers split on. Reading turns the file's line breaks into those, and writing
 * turns them back, so {@code read_file lines} counts the file's lines.
 */
public final class DebugNodes {

    public static final String OUTPUT_FILE = "output.txt";

    private DebugNodes() {
    }

    public static void register(ScriptRegistrar registrar) {
        registrar.register(ScriptNodes.getter("read_file", STRING,
                context -> read(context.host(Path.class).resolve(OUTPUT_FILE))));
        registrar.register(ScriptNodes.executor("write_file", STRING, (text, context) -> {
            write(context.host(Path.class).resolve(OUTPUT_FILE), text);
            return 1;
        }));
    }

    /** The file's contents, or an empty string if there is no file yet. */
    private static String read(Path file) {
        if (!Files.exists(file)) {
            return "";
        }
        try {
            String contents = Files.readString(file, StandardCharsets.UTF_8);
            return contents.replace("\r\n", "\n").replace("\n", ESCAPED_NEWLINE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + OUTPUT_FILE + ": " + e.getMessage(), e);
        }
    }

    /** Replaces the file's contents. */
    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text.replace(ESCAPED_NEWLINE, "\n"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + OUTPUT_FILE + ": " + e.getMessage(), e);
        }
    }
}
