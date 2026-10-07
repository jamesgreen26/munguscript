package g_mungus.munguscript.conformance;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Getter, mappers, executor: running commands and what their arguments can be. */
class ExecutionTest {

    @EngineTest
    void executorsTakeLiteralArguments(Harness h) {
        h.run("""
                paint red
                move 1 2
                set_level 3
                log "hi there"
                """);
        h.assertCalls(call("paint", Color.RED), call("move", new Point(1, 2)), call("set_level", 3),
                call("log", "hi there"));
    }

    @EngineTest
    void commandsRunInOrderAndSeeEarlierEffects(Harness h) {
        h.run("""
                set_level 4
                set_level value_of(level scale 2)
                log value_of(level as_string)
                """);
        h.assertCalls(call("set_level", 4), call("set_level", 8), call("log", "8"));
    }

    @EngineTest
    void aCommandReturnsWhatItsExecutorReturned(Harness h) {
        Harness counting = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.executor("count", INT, (value, context) -> value * 10));
        });
        assertEquals(List.of(30, 70), counting.run("""
                count 3
                count value_of(here x)
                """));
    }

    @EngineTest
    void relativeArgumentsResolveAgainstTheHostContext(Harness h) {
        h.run("move ~1 ~");
        h.assertCalls(call("move", new Point(101, 200)));
    }

    @EngineTest
    void anExecutorCanReadAnotherKindOfArgument(Harness h) {
        h.run("pick blue");
        h.assertCalls(call("pick", 2));
    }

    @EngineTest
    void aNarrowedArgumentRejectsValuesOutsideIt(Harness h) {
        h.failure("set_level 11");
        h.assertCalls();
    }

    @EngineTest
    void valueOfStandsInForAnArgument(Harness h) {
        h.world.level = 4;
        h.run("""
                set_level value_of(level)
                move value_of(here plus 1 2)
                move value_of(here plus value_of(origin plus 5 5))
                """);
        h.assertCalls(call("set_level", 4), call("move", new Point(8, 10)), call("move", new Point(12, 13)));
    }

    @EngineTest
    void valueOfGivesTheInputTypeEvenWhenTheArgumentIsAnotherKind(Harness h) {
        h.world.level = 1;
        h.run("pick value_of(level)");
        h.assertCalls(call("pick", 1));
    }

    @EngineTest
    void valueOfCanHoldQuotedParentheses(Harness h) {
        h.run("log value_of(message + \"(a) b)\")");
        h.assertCalls(call("log", "(a) b)"));
    }

    @EngineTest
    void anOpaqueValueCanPassThroughAChain(Harness h) {
        h.world.counter.value = 6;
        h.run("set_level value_of(counter value)");
        h.assertCalls(call("set_level", 6));
    }

    @EngineTest
    void overloadsArePickedByTarget(Harness h) {
        h.run("configure 3");
        h.block = "block_a";
        h.run("configure 4");
        h.block = "block_b";
        h.run("configure blue");
        h.assertCalls(call("configure_any", 3), call("configure_a", 4), call("configure_b", Color.BLUE));
    }

    @EngineTest
    void anOverloadMeantForAnotherTargetDoesNotRun(Harness h) {
        h.block = "block_a";
        assertEquals(List.of(0), h.run("configure blue"));
        h.assertCalls();
    }

    @EngineTest
    void anOverloadForThisTargetWinsOverAnEarlierUnrestrictedOne(Harness h) {
        Harness ordered = h.with((registrar, world) -> {
            registrar.register(ScriptNodes.executor("say", INT, (value, context) -> world.record("anywhere", value)));
            registrar.register(ScriptNodes.executor("say", INT, (value, context) -> world.record("stone", value))
                    .withApplicability(TestHost.Blocks.of("stone")));
        });
        ordered.run("say 1");
        ordered.block = "dirt";
        ordered.run("say 2");
        ordered.assertCalls(call("stone", 1), call("anywhere", 2));
    }

    @EngineTest
    void theFirstOfSeveralMatchingOverloadsRuns(Harness h) {
        Harness ordered = h.with((registrar, world) -> {
            registrar.register(ScriptNodes.executor("say", INT, (value, context) -> world.record("first", value)));
            registrar.register(ScriptNodes.executor("say", INT, (value, context) -> world.record("second", value)));
            registrar.register(ScriptNodes.executor("aim", INT, (value, context) -> world.record("aim_first", value))
                    .withApplicability(TestHost.Blocks.of("stone")));
            registrar.register(ScriptNodes.executor("aim", INT, (value, context) -> world.record("aim_second", value))
                    .withApplicability(TestHost.Blocks.of("stone")));
        });
        ordered.run("""
                say 1
                aim 2
                """);
        ordered.assertCalls(call("first", 1), call("aim_first", 2));
    }

    @EngineTest
    void anOverloadThatReadsMoreOfTheCommandIsTheOneThatParses(Harness h) {
        Harness overloaded = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.executor("go", INT, (value, context) -> world.record("go_int", value)));
            registrar.register(ScriptNodes.executor("go", TestTypes.POINT,
                    (point, context) -> world.record("go_point", point)));
        });
        overloaded.run("""
                go 5
                go 5 6
                """);
        overloaded.assertCalls(call("go_int", 5), call("go_point", new Point(5, 6)));
    }

    @EngineTest
    void aPlaceholderCannotRun(Harness h) {
        assertEquals("Argument placeholder %s must be replaced before execution",
                h.failure("set_level %s").reason());
        h.assertCalls();
    }

    @EngineTest
    void nothingRunsOutsideARun(Harness h) throws CommandSyntaxException {
        assertEquals(0, h.runWithoutARun("paint red"));
        assertEquals(0, h.runWithoutARun("set_level value_of(level crash)"));
        h.assertCalls();
    }

    @EngineTest
    void aFailedCommandHasNoEffect(Harness h) {
        h.failure("set_level value_of(level crash)");
        h.assertCalls();
        assertNull(h.execute("log \"x\"").failure());
    }
}
