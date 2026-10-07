package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.conformance.TestTypes.Point;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code if}, {@code unless} and {@code else}. */
class ConditionTest {
    /** Conditions that hold, and do not, at {@link Harness#POS}. */
    static final String TRUE = "here x == 7";
    static final String FALSE = "here x == 8";

    static String condition(boolean holds) {
        return holds ? TRUE : FALSE;
    }

    private static void assertLogs(Harness h, String command, String expected) {
        h.world.calls.clear();
        h.run(command);
        assertEquals(List.of(call("log", expected)), h.world.calls, command);
    }

    @EngineTest
    void ifAndUnless(Harness h) {
        h.run("if " + TRUE + " log yes");
        h.run("if " + FALSE + " log no");
        h.run("unless " + TRUE + " log no");
        h.run("unless " + FALSE + " log yes");
        h.assertCalls(call("log", "yes"), call("log", "yes"));
    }

    @EngineTest
    void aCommandWhoseConditionFailsReturnsNothing(Harness h) {
        assertEquals(List.of(0, 1), h.run("""
                if %s log no
                if %s log yes
                """.formatted(FALSE, TRUE)));
    }

    @EngineTest
    void elseRunsTheOtherBranch(Harness h) {
        assertLogs(h, "if " + TRUE + " log then else log otherwise", "then");
        assertLogs(h, "if " + FALSE + " log then else log otherwise", "otherwise");
        assertLogs(h, "unless " + TRUE + " log then else log otherwise", "otherwise");
        assertLogs(h, "unless " + FALSE + " log then else log otherwise", "then");
    }

    @EngineTest
    void elseIfChainsRunTheFirstBranchThatHolds(Harness h) {
        for (boolean a : new boolean[]{true, false}) {
            for (boolean b : new boolean[]{true, false}) {
                for (boolean c : new boolean[]{true, false}) {
                    String expected = a ? "a" : b ? "b" : c ? "c" : "none";
                    assertLogs(h, "if " + condition(a) + " log a else if " + condition(b) + " log b else if "
                            + condition(c) + " log c else log none", expected);
                }
            }
        }
    }

    @EngineTest
    void elseUnlessMixesWithElseIf(Harness h) {
        assertLogs(h, "if " + FALSE + " log a else unless " + TRUE + " log b else log c", "c");
        assertLogs(h, "if " + FALSE + " log a else unless " + FALSE + " log b else log c", "b");
    }

    @EngineTest
    void conditionsCanUseAnyChainThatEndsInABoolean(Harness h) {
        h.world.level = 25;
        h.world.favourite = Color.BLUE;
        h.run("""
                if level to_celsius warm log warm
                if favourite == blue log blue
                if here == 7 8 log here
                if level scale value_of(level) is_positive log positive
                if level > value_of(here x) log above
                """);
        h.assertCalls(call("log", "warm"), call("log", "blue"), call("log", "here"), call("log", "positive"),
                call("log", "above"));
    }

    @EngineTest
    void aValueOfInAConditionDoesNotDisturbTheConditionsOwnValue(Harness h) {
        h.world.level = 3;
        h.run("if level scale value_of(here x) == 21 log yes");
        h.assertCalls(call("log", "yes"));
    }

    @EngineTest
    void anyExecutorCanFollowACondition(Harness h) {
        h.run("""
                if %1$s move 1 2 else paint red
                if %2$s move 1 2 else paint red
                if %1$s set_level value_of(here x)
                """.formatted(TRUE, FALSE));
        h.assertCalls(call("move", new Point(1, 2)), call("paint", Color.RED), call("set_level", 7));
    }

    @EngineTest
    void onlyTheChosenBranchEvaluatesItsArgument(Harness h) {
        h.run("""
                if %1$s log a else set_level value_of(level crash)
                if %2$s set_level value_of(level crash) else log b
                if %2$s set_level value_of(level crash)
                """.formatted(TRUE, FALSE));
        h.assertCalls(call("log", "a"), call("log", "b"));
    }

    @EngineTest
    void aConditionMustBeABoolean(Harness h) {
        h.failure("if level log x");
        h.failure("if here log x");
        h.failure("unless message log x");
        h.assertCalls();
    }

    @EngineTest
    void aConditionNeedsSomethingToRun(Harness h) {
        h.failure("if " + TRUE);
        h.failure("if " + TRUE + " log a else");
        h.failure("if");
        h.assertCalls();
    }

    @EngineTest
    void elseOnlyFollowsAConditionalCommand(Harness h) {
        h.failure("log a else log b");
        h.assertCalls();
    }

    @EngineTest
    void elseAfterAnUnconditionalCommandDoesNotParse(Harness h) {
        assertTrue(h.parsesFully("if " + TRUE + " log a else log b"));
        assertFalse(h.parsesFully("log a else log b"));
        assertFalse(h.parsesFully("paint red else paint blue"));
        assertFalse(h.parsesFully("set_level value_of(level) else log b"));
    }

    @EngineTest
    void elseAfterTheLastBranchOfAChainDoesNotParse(Harness h) {
        assertTrue(h.parsesFully("if " + TRUE + " log a else if " + FALSE + " log b else log c"));
        assertFalse(h.parsesFully("if " + TRUE + " log a else log b else log c"));
        assertFalse(h.parsesFully("if " + TRUE + " log a else if " + FALSE + " log b else log c else log d"));
    }

    @EngineTest
    void elseAfterTheLastBranchOfAChainFailsAndRunsNothing(Harness h) {
        h.failure("if " + TRUE + " log a else log b else log c");
        h.failure("if " + FALSE + " log a else log b else log c");
        h.assertCalls();
    }

    @EngineTest
    void elseAfterAnUnconditionalCommandIsPointedAt(Harness h) {
        assertEquals("else", h.failure("log a else log b").faultText());
        assertEquals("else", h.failure("paint red else paint blue").faultText());
    }

    @EngineTest
    void elseIsOnlySuggestedAfterAConditionalCommand(Harness h) {
        assertTrue(h.suggest("if " + TRUE + " log a ", null).contains("else"));
        assertTrue(h.suggest("unless " + TRUE + " paint red ", null).contains("else"));
        assertFalse(h.suggest("log a ", null).contains("else"));
        assertFalse(h.suggest("paint red ", null).contains("else"));
        assertFalse(h.suggest("if " + TRUE + " log a else log b ", null).contains("else"));
    }
}
