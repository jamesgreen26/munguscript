package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The engine's own pre-processor: {@code #def name = expression} at the top of a script. */
class AliasTest {

    @EngineTest
    void anAliasStandsInForItsExpressionInsideValueOf(Harness h) {
        h.world.level = 3;
        h.run("""
                #def lvl = level
                set_level value_of(lvl scale 2)
                """);
        h.assertCalls(call("set_level", 6));
    }

    @EngineTest
    void anAliasCanBuildOnAnEarlierOne(Harness h) {
        h.run("""
                #def spot = here plus 1 1
                #def spot_x = spot x
                set_level value_of(spot_x)
                move value_of(spot)
                """);
        h.assertCalls(call("set_level", 8), call("move", new Point(8, 9)));
    }

    @EngineTest
    void anAliasWorksInNestedValueOfs(Harness h) {
        h.run("""
                #def spot = here plus 1 1
                move value_of(origin plus value_of(spot))
                """);
        h.assertCalls(call("move", new Point(8, 9)));
    }

    @EngineTest
    void anAliasRunsItsGettersAgainEachTimeItIsUsed(Harness h) {
        h.run("""
                #def lvl = level
                set_level 2
                set_level value_of(lvl scale 3)
                set_level value_of(lvl scale 3)
                """);
        h.assertCalls(call("set_level", 2), call("set_level", 6), call("set_level", 18));
    }

    @EngineTest
    void anAliasCanBeAWholeCondition(Harness h) {
        h.run("""
                #def at_seven = here x == 7
                if at_seven log yes else log no
                unless at_seven log yes else log no
                """);
        h.assertCalls(call("log", "yes"), call("log", "no"));
    }

    @EngineTest
    void anAliasCanBeTheConditionAfterElseIf(Harness h) {
        h.run("""
                #def at_seven = here x == 7
                if here x == 0 log a else if at_seven log b
                if here x == 0 log a else unless at_seven log b else log c
                """);
        h.assertCalls(call("log", "b"), call("log", "c"));
    }

    @EngineTest
    void aFailureInAnAliasAfterElseIfPointsAtTheAlias(Harness h) {
        ScriptFailure failure = h.failure("""
                #def broken = level crash is_positive
                if here x == 0 log a else if broken log b
                """);
        assertEquals("Could not evaluate 'crash': the mapper crashed", failure.reason());
        assertEquals("broken", failure.faultText());
    }

    @EngineTest
    void elseInsideAStringOrValueOfIsNotTheKeyword(Harness h) {
        h.world.message = "m";
        h.run("""
                #def at_seven = here x == 7
                if at_seven log "else if at_seven" else log b
                if at_seven log value_of(message + "else") else if at_seven log c
                """);
        h.assertCalls(call("log", "else if at_seven"), call("log", "melse"));
    }

    @EngineTest
    void anAliasCanStartAConditionThatGoesOn(Harness h) {
        h.world.level = 25;
        h.run("""
                #def temperature = level to_celsius
                if temperature warm log warm
                """);
        h.assertCalls(call("log", "warm"));
    }

    @EngineTest
    void anAliasIsOnlyExpandedWhereAnExpressionStarts(Harness h) {
        h.world.message = "hi";
        h.run("""
                #def greeting = message
                log greeting
                log value_of(greeting + "greeting")
                """);
        h.assertCalls(call("log", "greeting"), call("log", "higreeting"));
    }

    @EngineTest
    void commentsBlankLinesAndWindowsLineEndingsAreAllowedInTheBlock(Harness h) {
        h.world.level = 4;
        h.run("# a comment\r\n\r\n#def lvl = level\r\nset_level value_of(lvl)\r\n");
        h.assertCalls(call("set_level", 4));
    }

    @EngineTest
    void aScriptWithoutAliasesRunsUnchanged(Harness h) {
        h.run("""
                # just a comment
                log "#def x = y"
                """);
        h.assertCalls(call("log", "#def x = y"));
    }

    @EngineTest
    void badDefinitionsAreReportedByLineAndNothingRuns(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #def scale = level
                #def doubled = scale 2
                #def = level
                #def empty =
                log "x"
                """);
        assertEquals(List.of(
                PreProcessDiagnostic.atLine(0, "Alias name 'scale' conflicts with a mapper"),
                PreProcessDiagnostic.atLine(1, "Alias expression must start with a getter or previous alias"),
                PreProcessDiagnostic.atLine(2, "Invalid alias declaration"),
                PreProcessDiagnostic.atLine(3, "Alias expression cannot be empty")), diagnostics);
        h.assertCalls();
    }

    @EngineTest
    void anAliasCanBeDefinedTwiceWithTheSameExpression(Harness h) {
        h.world.level = 4;
        h.run("""
                #def lvl = level
                #def lvl = level
                set_level value_of(lvl)
                """);
        h.assertCalls(call("set_level", 4));
    }

    @EngineTest
    void aLaterAliasReplacesAnEarlierOneOfTheSameName(Harness h) {
        h.world.level = 3;
        h.run("""
                #def n = level
                #def n = level scale 2
                set_level value_of(n)
                """);
        h.assertCalls(call("set_level", 6));
    }

    @EngineTest
    void aReplacedAliasCanHaveADifferentType(Harness h) {
        h.world.message = "hi";
        h.run("""
                #def a = level
                #def a = message
                log value_of(a)
                """);
        h.assertCalls(call("log", "hi"));
    }

    @EngineTest
    void aReplacedConditionAliasIsTheLaterOne(Harness h) {
        h.run("""
                #def ok = %s
                #def ok = %s
                if ok log yes else log no
                """.formatted(ConditionTest.TRUE, ConditionTest.FALSE));
        h.assertCalls(call("log", "no"));
    }

    @EngineTest
    void declarationsBeforeAReplacementKeepTheEarlierDefinition(Harness h) {
        h.world.level = 1;
        h.run("""
                #def a = level
                #def b = a scale 2
                #def a = level scale 3
                log value_of(b as_string)
                log value_of(a as_string)
                """);
        h.assertCalls(call("log", "2"), call("log", "3"));
    }

    @EngineTest
    void aReplacementCanBuildOnTheDefinitionItReplaces(Harness h) {
        h.world.level = 2;
        h.run("""
                #def n = level
                #def n = n scale 5
                set_level value_of(n)
                """);
        h.assertCalls(call("set_level", 10));
    }

    @EngineTest
    void anAliasCannotUseOneDefinedAfterIt(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #def first = second x
                #def second = here
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(0,
                "Alias expression must start with a getter or previous alias")), diagnostics);
    }

    @EngineTest
    void theDeclarationBlockIsUsedUpAndEndsAtTheFirstCommand(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare("""
                # a comment
                #def lvl = level
                log "x"
                # not a comment any more
                """);
        assertEquals(Set.of(0, 1), prepared.consumedLines());
        assertEquals("Unknown command '#'", h.failure("""
                log "x"
                # not a comment any more
                """).reason());
    }

    @EngineTest
    void aFailureInsideAnAliasPointsAtTheAlias(Harness h) {
        ScriptFailure failure = h.failure("""
                #def broken = level crash
                set_level value_of(broken scale 2)
                """);
        assertEquals("Could not evaluate 'crash': the mapper crashed", failure.reason());
        assertEquals("set_level value_of(broken scale 2)", failure.playerCommand());
        assertEquals("set_level value_of(level crash scale 2)", failure.executedCommand());
        assertEquals("broken", failure.faultText());
    }

    @EngineTest
    void aFailureAfterAnAliasPointsAtWhatThePlayerWrote(Harness h) {
        ScriptFailure failure = h.failure("""
                #def lvl = level
                set_level value_of(lvl crash)
                """);
        assertEquals("crash", failure.faultText());
    }

    @EngineTest
    void aliasTokensKnowTheirType(Harness h) {
        Map<String, TypeKey> types = new HashMap<>();
        for (PreProcessorToken token : h.prepare("""
                #def spot = here plus 1 1
                #def spot_x = spot x
                #def at_seven = here x == 7
                #def counted = counter
                """).tokens()) {
            assertEquals(PreProcessorToken.Placement.EXPRESSION, token.placement());
            types.put(token.text(), token.valueType());
        }
        assertEquals(Map.of("spot", TestTypes.POINT.key(), "spot_x", BuiltInTypes.INT.key(),
                "at_seven", BuiltInTypes.BOOLEAN.key(), "counted", TestTypes.COUNTER.key()), types);
    }

    @EngineTest
    void aCommandWithNoScriptAroundItIsPreparedFromNothing(Harness h) {
        CommandPreProcessor.Prepared prepared = h.engine.aliases().prepare(List.of(), h.preProcessContext());
        assertEquals("log \"x\"", prepared.process("log \"x\"", h.preProcessContext()).command());
        assertEquals(List.of(), prepared.diagnostics());
    }
}
