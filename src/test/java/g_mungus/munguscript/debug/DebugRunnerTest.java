package g_mungus.munguscript.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRunnerTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private boolean run(String script) {
        return new DebugRunner(directory).run("run/main.mungus", script.lines().toList(),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    private String output() throws IOException {
        return Files.readString(directory.resolve(DebugNodes.OUTPUT_FILE));
    }

    @Test
    void commandsRunInOrder() throws IOException {
        assertTrue(run("""
                write_file "hello"
                write_file value_of(read_file + " world")
                """), errors.toString());
        assertEquals("hello world", output());
    }

    @Test
    void readingAFileThatIsNotThereGivesNothing() throws IOException {
        assertTrue(run("write_file value_of(read_file + \"x\")"), errors.toString());
        assertEquals("x", output());
    }

    @Test
    void lineBreaksBecomeScriptLinesAndBack() throws IOException {
        Files.writeString(directory.resolve(DebugNodes.OUTPUT_FILE), "one\ntwo\r\nthree");
        assertTrue(run("""
                if read_file lines == 3 write_file value_of(read_file remove_line 2)
                """), errors.toString());
        assertEquals("one\nthree", output());
    }

    @Test
    void aliasesAndConditionsWork() throws IOException {
        assertTrue(run("""
                # Writes a greeting, then says whether it was long.
                #def text = read_file
                write_file "hi"
                if text lines > 1 write_file "many lines" else write_file value_of(text + "!")
                """), errors.toString());
        assertEquals("hi!", output());
    }

    @Test
    void theFirstFailureStopsTheScriptAndIsPointedAt() throws IOException {
        assertFalse(run("""
                write_file "before"
                write_file value_of(read_file lines % 0 as_string)
                write_file "after"
                """));
        assertEquals("before", output());
        assertEquals("""
                run/main.mungus:2: Could not evaluate '%': / by zero
                    write_file value_of(read_file lines % 0 as_string)
                                                          ^
                """, errors.toString());
    }

    @Test
    void aBadAliasStopsTheScriptBeforeAnythingRuns() {
        assertFalse(run("""
                #def + = read_file
                write_file "x"
                """));
        assertFalse(Files.exists(directory.resolve(DebugNodes.OUTPUT_FILE)));
        assertTrue(errors.toString().startsWith("run/main.mungus:1: Invalid alias declaration"), errors.toString());
    }
}
