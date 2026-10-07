package g_mungus.munguscript;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which packages may use which. Only code counts: comments may mention any package.
 *
 * <ul>
 *     <li>The language uses nothing else in the library.</li>
 *     <li>The engine's API never uses its implementation; {@code MungusScript} finds it at run time.
 *         Nor does it use the runner, which is built on it.</li>
 *     <li>The runner uses only the engine's API and the language.</li>
 *     <li>The conformance tests only use the language and the engine's API, so they can test any
 *         engine implementation.</li>
 * </ul>
 */
class PackageDependencyTest {
    private static final Path MAIN = Path.of("src/main/java/g_mungus/munguscript");
    private static final Path TEST = Path.of("src/test/java/g_mungus/munguscript");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    void theLanguageUsesNothingElseInTheLibrary() {
        assertEquals(List.of(), offenders(MAIN.resolve("language"), "engine", "engine_impl", "runner", "debug"));
    }

    @Test
    void theEngineApiDoesNotUseItsImplementation() {
        assertEquals(List.of(), offenders(MAIN.resolve("engine"), "engine_impl", "runner"));
    }

    @Test
    void theRunnerUsesOnlyTheEngineApiAndTheLanguage() {
        assertEquals(List.of(), offenders(MAIN.resolve("runner"), "engine_impl", "debug"));
    }

    @Test
    void theConformanceTestsDoNotUseTheImplementation() {
        assertEquals(List.of(), offenders(TEST.resolve("conformance"), "engine_impl"));
    }

    /** Files under {@code directory} whose code refers to any of {@code packages}. */
    private static List<String> offenders(Path directory, String... packages) {
        Pattern forbidden = Pattern.compile("g_mungus\\.munguscript\\.(" + String.join("|", packages) + ")\\b");
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> forbidden.matcher(code(file)).find())
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String code(Path file) {
        try {
            return COMMENTS.matcher(Files.readString(file)).replaceAll("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
