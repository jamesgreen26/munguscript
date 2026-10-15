package g_mungus.munguscript.conformance;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quoted string literals: Brigadier's escapes, plus {@code \n}, which stays the two characters every
 * script string breaks lines at.
 */
class StringLiteralTest {

    @EngineTest
    void lineBreakEscapeIsKeptAsWritten(Harness h) {
        h.run("log \"a\\nb\"");
        h.assertCalls(call("log", "a\\nb"));
    }

    @EngineTest
    void escapedBackslashBeforeNReadsTheSame(Harness h) {
        h.run("log \"a\\\\nb\"");
        h.assertCalls(call("log", "a\\nb"));
    }

    @EngineTest
    void lineBreaksCountAsLines(Harness h) {
        h.run("log value_of(literal_of(\"a\\nb\\nc\") lines as_string)");
        h.assertCalls(call("log", "3"));
    }

    @EngineTest
    void variableKeepsLineBreaks(Harness h) {
        h.run("#var text = \"x\\ny\"\nlog value_of(text)\nlog value_of(text get_line 2)");
        h.assertCalls(call("log", "x\\ny"), call("log", "y"));
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
