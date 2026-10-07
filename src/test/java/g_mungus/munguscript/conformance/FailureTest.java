package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failures as the engine describes them: a reason, and where in the player's text. */
class FailureTest {

    @EngineTest
    void aFailureStopsTheScriptAtItsLine(Harness h) {
        Harness.Outcome outcome = h.execute("""
                log "before"
                frobnicate
                log "after"
                """);
        assertEquals(1, outcome.line());
        assertEquals("frobnicate", outcome.failure().playerCommand());
        h.assertCalls(call("log", "before"));
    }

    @EngineTest
    void anUnknownCommandIsNamed(Harness h) {
        ScriptFailure failure = h.failure("frobnicate 9");
        assertEquals("Unknown command 'frobnicate'", failure.reason());
        assertEquals("frobnicate", failure.faultText());
    }

    @EngineTest
    void anIncompleteCommandHasNoPosition(Harness h) {
        ScriptFailure failure = h.failure("paint");
        assertEquals("The command is incomplete", failure.reason());
        assertNull(failure.faultRange());
    }

    @EngineTest
    void somethingAfterACompleteCommandIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("paint red blue");
        assertEquals("'blue' is not valid here", failure.reason());
        assertEquals("blue", failure.faultText());
    }

    @EngineTest
    void aBadArgumentIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("set_level banana");
        assertEquals("Expected integer", failure.reason());
        assertEquals("banana", failure.faultText());
        failure = h.failure("set_level 11");
        assertTrue(failure.reason().contains("10"), failure.reason());
        assertEquals("11", failure.faultText());
    }

    @EngineTest
    void aValueOfOfTheWrongKindSaysWhatItGives(Harness h) {
        ScriptFailure failure = h.failure("set_level value_of(favourite)");
        assertEquals("value_of(favourite) gives test:color, but set_level needs int", failure.reason());
        assertEquals("value_of(favourite)", failure.faultText());
        failure = h.failure("set_level value_of(frobnicate)");
        assertEquals("'frobnicate' is not a known value in value_of(frobnicate)", failure.reason());
        assertEquals("value_of(frobnicate)", failure.faultText());
    }

    @EngineTest
    void valueOfExplanations(Harness h) {
        assertEquals("value_of() is empty, and set_level needs int", h.failure("set_level value_of()").reason());
        assertEquals("value_of(level scale) is incomplete: 'scale' needs a value after it",
                h.failure("set_level value_of(level scale)").reason());
        assertEquals("In value_of(level scale x), 'x' is not a valid value for 'scale'",
                h.failure("set_level value_of(level scale x)").reason());
        assertEquals("In value_of(level frob), 'frob' cannot follow int",
                h.failure("set_level value_of(level frob)").reason());
        assertEquals("value_of(here) gives test:point, but set_level needs int",
                h.failure("set_level value_of(here)").reason());
        assertEquals("value_of(level) gives int, which cannot be turned into test:counter",
                h.engine.describe(failureOf(() -> h.valueOf("level", TestTypes.COUNTER)), "x", "x",
                        SourceMap.IDENTITY).reason());
    }

    @EngineTest
    void aValueOfOfTheWrongTypeIsRejectedWhileParsing(Harness h) {
        assertTrue(h.parsesFully("set_level value_of(level)"));
        for (String command : List.of("set_level value_of(here)", "move value_of(level)", "log value_of(favourite)",
                "paint value_of(message)")) {
            assertFalse(h.parsesFully(command), command);
            h.failure(command);
        }
        h.assertCalls();
    }

    @EngineTest
    void aNestedValueOfOfTheWrongTypeIsRejectedWhileParsing(Harness h) {
        assertTrue(h.parsesFully("move value_of(here plus value_of(origin plus value_of(here)))"));
        assertFalse(h.parsesFully("move value_of(here plus value_of(origin plus value_of(level)))"));
        assertFalse(h.parsesFully("set_level value_of(level scale value_of(here))"));
    }

    @EngineTest
    void aValueOfOfTheWrongTypeInAConditionIsPointedAt(Harness h) {
        assertFalse(h.parsesFully("if level > value_of(here) log a"));
        ScriptFailure failure = h.failure("if level > value_of(here) log a");
        assertEquals("value_of(here) gives test:point, but > needs int", failure.reason());
        assertEquals("value_of(here)", failure.faultText());
        h.assertCalls();
    }

    @EngineTest
    void aValueOfOfTheWrongTypeInABranchThatWouldNotRunIsStillRejected(Harness h) {
        for (String command : List.of("if " + ConditionTest.FALSE + " set_level value_of(here) else log b",
                "if " + ConditionTest.TRUE + " log a else set_level value_of(here)")) {
            assertFalse(h.parsesFully(command), command);
            h.failure(command);
        }
        h.assertCalls();
    }

    @EngineTest
    void aValueOfOfTheWrongTypeInABranchThatRunsIsPointedAt(Harness h) {
        assertFalse(h.parsesFully("if " + ConditionTest.TRUE + " set_level value_of(here) else log b"));
        ScriptFailure failure = h.failure("if " + ConditionTest.TRUE + " set_level value_of(here) else log b");
        assertEquals("value_of(here) gives test:point, but set_level needs int", failure.reason());
        assertEquals("value_of(here)", failure.faultText());
        h.assertCalls();
    }

    @EngineTest
    void aValueOfOfTheWrongTypeThroughAnAliasIsPointedAtTheAlias(Harness h) {
        var prepared = h.prepare("""
                #def spot = here
                set_level value_of(spot)
                """);
        String expanded = prepared.process("set_level value_of(spot)", h.preProcessContext()).command();
        assertFalse(h.parsesFully(expanded), expanded);
        ScriptFailure failure = h.failure("""
                #def spot = here
                set_level value_of(spot)
                """);
        assertTrue(failure.reason().contains("set_level needs int"), failure.reason());
        assertEquals("value_of(spot)", failure.faultText());
        h.assertCalls();
    }

    @EngineTest
    void aMapperArgumentValueOfIsNamedForTheMapper(Harness h) {
        assertEquals("value_of(here) gives test:point, but scale needs int",
                h.failure("set_level value_of(level scale value_of(here))").reason());
    }

    @EngineTest
    void anUnclosedValueOfIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("set_level value_of(level");
        assertTrue(failure.faultRange() != null && failure.faultText().startsWith("value_of("), failure.toString());
    }

    @EngineTest
    void aFailureInsideValueOfIsPointedAtInTheCommand(Harness h) {
        ScriptFailure failure = h.failure("set_level value_of(level crash)");
        assertEquals("Could not evaluate 'crash': the mapper crashed", failure.reason());
        assertEquals("crash", failure.faultText());
        failure = h.failure("move value_of(here plus value_of(origin plus value_of(frobnicate)))");
        assertEquals("'frobnicate' is not a known value in value_of(frobnicate)", failure.reason());
        assertEquals("value_of(frobnicate)", failure.faultText());
    }

    @EngineTest
    void aFailingGetterIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("set_level value_of(explode)");
        assertEquals("Could not evaluate 'explode': the getter exploded", failure.reason());
        assertEquals("explode", failure.faultText());
    }

    @EngineTest
    void aFailureInAConditionIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("if level crash is_positive log x");
        assertEquals("Could not evaluate 'crash': the mapper crashed", failure.reason());
        assertEquals("crash", failure.faultText());
    }

    @EngineTest
    void anExecutorsOwnFailureHasNoPosition(Harness h) {
        ScriptFailure failure = h.failure("boom 1");
        assertEquals("the executor blew up", failure.reason());
        assertNull(failure.faultRange());
    }

    @EngineTest
    void aReasonIsNeverAnExceptionName(Harness h) {
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.getter("silent", INT, context -> {
                throw new RuntimeException();
            }));
            registrar.register(ScriptNodes.getter("wrapped", INT, context -> {
                throw new RuntimeException(new IllegalStateException("the real reason"));
            }));
        });
        String silent = custom.failure("set_level value_of(silent)").reason();
        String wrapped = custom.failure("set_level value_of(wrapped)").reason();
        assertEquals("Could not evaluate 'silent': The command failed for an unknown reason", silent);
        assertEquals("Could not evaluate 'wrapped': the real reason", wrapped);
        for (String reason : List.of(silent, wrapped)) {
            assertFalse(reason.contains("Exception"), reason);
        }
    }

    @EngineTest
    void theCommandIsReportedAsWrittenAndAsRun(Harness h) {
        ScriptFailure failure = h.failure("""
                #def lvl = level
                set_level value_of(lvl crash)
                """);
        assertEquals("set_level value_of(lvl crash)", failure.playerCommand());
        assertEquals("set_level value_of(level crash)", failure.executedCommand());
    }

    @EngineTest
    void typesAreNamedByPathInTheEnginesAndHostsNamespaces(Harness h) {
        assertEquals("int", h.engine.typeName(BuiltInTypes.INT.key()));
        assertEquals("thing", h.engine.typeName(new TypeKey("host", "thing")));
        assertEquals("test:color", h.engine.typeName(TestTypes.COLOR.key()));
    }

    private static Throwable failureOf(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("Expected a failure");
    }
}
