package g_mungus.munguscript.conformance;

import java.util.Map;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quoted string literals: Brigadier's escapes, plus {@code \n} for a line break. A string's value
 * holds real line breaks; only script text escapes them.
 */
class StringLiteralTest {

    @EngineTest
    void lineBreakEscapeIsALineBreak(Harness h) {
        h.run("log \"a\\nb\"");
        h.assertCalls(call("log", "a\nb"));
    }

    @EngineTest
    void escapedBackslashBeforeNIsABackslashAndAnN(Harness h) {
        h.run("log \"a\\\\nb\"");
        h.assertCalls(call("log", "a\\nb"));
    }

    @EngineTest
    void lineBreaksCountAsLines(Harness h) {
        h.run("log value_of(literal_of(\"a\\nb\\nc\") lines as_string)");
        h.assertCalls(call("log", "3"));
    }

    @EngineTest
    void declaredVariableKeepsLineBreaks(Harness h) {
        h.run("#var text = \"x\\ny\"\nlog value_of(text)\nlog value_of(text get_line 2)");
        h.assertCalls(call("log", "x\ny"), call("log", "y"));
    }

    /** A received value is written into the command as a literal, and must read back as it was. */
    @EngineTest
    void receivedValueWithLineBreaksAndEscapesArrivesUnchanged(Harness h) {
        String value = "write_page \"a\\nb\"\nset \\ (x) # y";
        h.variables = Map.of("text", value);
        h.run("#var text = none\nlog value_of(text)\nlog value_of(text lines as_string)");
        h.assertCalls(call("log", value), call("log", "2"));
    }

    @EngineTest
    void quotesBackslashesBracketsAndHashesSurvive(Harness h) {
        h.run("log \"say \\\"hi\\\" \\\\ (x) # y\"");
        h.assertCalls(call("log", "say \"hi\" \\ (x) # y"));
    }

    @EngineTest
    void otherEscapesAreStillRefused(Harness h) {
        assertTrue(h.failure("log \"a\\tb\"").reason().contains("Invalid escape"),
                () -> h.failure("log \"a\\tb\"").reason());
    }
}
