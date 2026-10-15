package g_mungus.munguscript.debug;

import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;

/**
 * {@code read_file} and {@code write_file}, on {@code output.txt} in the script's directory.
 *
 * <p>The file's line breaks are the string's, so {@code read_file lines} counts the file's lines.
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
            return contents.replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + OUTPUT_FILE + ": " + e.getMessage(), e);
        }
    }

    /** Replaces the file's contents. */
    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + OUTPUT_FILE + ": " + e.getMessage(), e);
        }
    }
}
