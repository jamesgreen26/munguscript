package g_mungus.munguscript.conformance;

import g_mungus.munguscript.language.node.ScriptNodes;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a conditional command returns: the result of the one branch that ran, or 0 if none did. Each
 * branch's executor runs at most once, wherever in the line it stands.
 */
class BranchResultTest {
    private static final String TRUE = ConditionTest.TRUE;
    private static final String FALSE = ConditionTest.FALSE;

    /** The test nodes plus {@code count}, which returns ten times its argument. */
    private static Harness counting(Harness h) {
        return h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.executor("count", INT, (value, context) -> {
                world.record("count", value);
                return value * 10;
            }));
        });
    }

    private static void assertResult(Harness h, String command, int result, World.Call... calls) {
        h.world.calls.clear();
        assertEquals(List.of(result), h.run(command), command);
        assertEquals(List.of(calls), h.world.calls, command);
    }

    @EngineTest
    void aConditionThatHoldsReturnsItsExecutorsResult(Harness h) {
        Harness c = counting(h);
        assertResult(c, "if " + TRUE + " count 1", 10, call("count", 1));
        assertResult(c, "unless " + FALSE + " count 2", 20, call("count", 2));
    }

    @EngineTest
    void aConditionThatFailsReturnsNothing(Harness h) {
        Harness c = counting(h);
        assertResult(c, "if " + FALSE + " count 1", 0);
        assertResult(c, "if " + FALSE + " count 1 else if " + FALSE + " count 2", 0);
    }

    @EngineTest
    void theFirstBranchsResultIsReturnedWhenItRuns(Harness h) {
        Harness c = counting(h);
        assertResult(c, "if " + TRUE + " count 1 else count 2", 10, call("count", 1));
        assertResult(c, "if " + TRUE + " count 1 else if " + TRUE + " count 2 else count 3", 10, call("count", 1));
    }

    @EngineTest
    void aLaterBranchsResultIsReturnedWhenItRuns(Harness h) {
        Harness c = counting(h);
        assertResult(c, "if " + FALSE + " count 1 else count 2", 20, call("count", 2));
        assertResult(c, "if " + FALSE + " count 1 else if " + TRUE + " count 2 else count 3", 20, call("count", 2));
        assertResult(c, "if " + FALSE + " count 1 else if " + FALSE + " count 2 else count 3", 30, call("count", 3));
        assertResult(c, "unless " + TRUE + " count 1 else unless " + TRUE + " count 2 else count 3", 30,
                call("count", 3));
    }

    @EngineTest
    void anOverloadThatDoesNotApplyInTheChosenBranchReturnsNothing(Harness h) {
        h.block = "block_a";
        assertEquals(List.of(0), h.run("if " + TRUE + " configure blue else log x"));
        h.assertCalls();
    }
}
