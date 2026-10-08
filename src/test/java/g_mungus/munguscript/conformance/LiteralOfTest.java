package g_mungus.munguscript.conformance;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code literal_of(...)}: a literal of a primitive type where an expression starts, standing as a getter does. */
class LiteralOfTest {

    @EngineTest
    void aLiteralIsTheFirstPrimitiveTypeThatReadsIt(Harness h) {
        h.run("""
                log value_of(literal_of(5) as_string)
                log value_of(literal_of(5.5) as_string)
                log value_of(literal_of(true) as_string)
                log value_of(literal_of("a b"))
                log value_of(literal_of(hello))
                log value_of(literal_of("5") + "1")
                set_level value_of(literal_of(5) + 1)
                """);
        h.assertCalls(call("log", "5"), call("log", "5.50"), call("log", "true"), call("log", "a b"),
                call("log", "hello"), call("log", "51"), call("set_level", 6));
    }

    @EngineTest
    void aLiteralCanStartACondition(Harness h) {
        h.run("""
                if literal_of(true) log a
                if literal_of(3) > 2 log b
                unless literal_of("x") == "x" log never else log c
                """);
        h.assertCalls(call("log", "a"), call("log", "b"), call("log", "c"));
    }

    @EngineTest
    void bracketsInsideQuotesAreText(Harness h) {
        h.run("log value_of(literal_of(\")(\") + \"!\")");
        h.assertCalls(call("log", ")(!"));
    }

    @EngineTest
    void whatNoPrimitiveTypeReadsIsExplained(Harness h) {
        assertEquals("In value_of(literal_of(a b) + \"x\"), 'a b' is not a literal: write a number, true or false,"
                + " or a string", h.failure("log value_of(literal_of(a b) + \"x\")").reason());
        assertEquals("In value_of(literal_of()), literal_of() is empty: write a number, true or false, or a string",
                h.failure("log value_of(literal_of())").reason());
        assertEquals("literal_of( is missing its closing )", h.failure("if literal_of(5 log x").reason());
        assertEquals("'a b' is not a literal: write a number, true or false, or a string",
                h.failure("if literal_of(a b) == \"a\" log x").reason());
    }

    @EngineTest
    void anUnknownConditionIsStillReportedAsBefore(Harness h) {
        assertEquals("'frob' is not valid here", h.failure("if frob log x").reason());
    }

    @EngineTest
    void itIsHighlightedAsAGetterAroundAnArgument(Harness h) {
        assertEquals(List.of("EXECUTOR:log", "ARGUMENT:value_of(", "GETTER:literal_of(", "ARGUMENT:\"x\"", "GETTER:)",
                        "MAPPER:+", "ARGUMENT:\"y\"", "ARGUMENT:)"),
                HighlightTest.highlights(h.engine, h, "log value_of(literal_of(\"x\") + \"y\")", null));
        assertEquals(List.of("KEYWORD:if", "GETTER:literal_of(", "ARGUMENT:3", "GETTER:)", "MAPPER:>", "ARGUMENT:2",
                        "EXECUTOR:log", "ARGUMENT:big"),
                HighlightTest.highlights(h.engine, h, "if literal_of(3) > 2 log big", null));
    }

    @EngineTest
    void itIsSuggestedOnceItHasBeenStarted(Harness h) {
        List<String> started = h.suggest("log value_of(lit", null);
        assertEquals(1, started.stream().filter("literal_of("::equals).count(), started.toString());
        assertTrue(h.suggest("if lit", null).contains("literal_of("));
        assertFalse(h.suggest("log value_of(", null).contains("literal_of("));
    }

    @EngineTest
    void anAliasCanBeALiteralOf(Harness h) {
        h.run("""
                #def five = literal_of(5)
                set_level value_of(five * 2)
                """);
        h.assertCalls(call("set_level", 10));
    }
}
