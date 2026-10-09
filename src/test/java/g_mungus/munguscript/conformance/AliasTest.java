package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                PreProcessDiagnostic.atLine(1, "Alias must be a literal, or an expression that starts with a getter or previous alias"),
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
                "Alias must be a literal, or an expression that starts with a getter or previous alias")), diagnostics);
    }

    @EngineTest
    void commentsMayStandOnAnyLine(Harness h) {
        h.world.level = 4;
        CommandPreProcessor.Prepared prepared = h.prepare("""
                # a comment
                #def lvl = level
                log "x"
                    # indented, between commands
                set_level value_of(lvl)
                # last
                """);
        assertEquals(Set.of(0, 1, 3, 5), prepared.consumedLines());
        h.run("""
                log "x"
                # between commands
                set_level value_of(level)
                """);
        h.assertCalls(call("log", "x"), call("set_level", 4));
    }

    @EngineTest
    void aDefinitionAfterTheFirstCommandIsAProblemAndNothingRuns(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #def lvl = level
                log "x"
                #def later = level
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(2, "Aliases must be defined before the first command")),
                diagnostics);
        h.assertCalls();
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

    @EngineTest
    void aLiteralAliasStartsAnExpressionAsALiteralOf(Harness h) {
        h.world.level = 3;
        h.run("""
                #def greeting = "hello there"
                #def loud = greeting + "!"
                log value_of(greeting)
                log value_of(greeting + "x")
                log value_of(loud)
                if greeting == "hello there" log yes
                if level > 0 log value_of(greeting) else log never
                """);
        h.assertCalls(call("log", "hello there"), call("log", "hello therex"), call("log", "hello there!"),
                call("log", "yes"), call("log", "hello there"));
    }

    @EngineTest
    void aLiteralAliasIsNotExpandedWhereAnArgumentGoes(Harness h) {
        h.world.message = "m";
        h.run("""
                #def greeting = "hello"
                #def n = 5
                log greeting
                log value_of(message + greeting)
                log "greeting"
                set_level value_of(n)
                """);
        h.assertCalls(call("log", "greeting"), call("log", "mgreeting"), call("log", "greeting"),
                call("set_level", 5));
        // Left as the word n, which is no int.
        ScriptFailure failure = h.failure("""
                #def n = 5
                set_level n
                """);
        assertEquals("set_level n", failure.executedCommand());
        assertEquals("n", failure.faultText());
    }

    @EngineTest
    void aStringAliasKeepsItsEscapes(Harness h) {
        h.run("""
                #def quoted = "say \\"hi\\""
                log value_of(quoted)
                """);
        h.assertCalls(call("log", "say \"hi\""));
    }

    @EngineTest
    void aLiteralAliasCanBeNamedByAnother(Harness h) {
        h.run("""
                #def first = "one"
                #def second = first
                #def first = "two"
                log value_of(second)
                log value_of(first)
                """);
        h.assertCalls(call("log", "one"), call("log", "two"));
    }

    @EngineTest
    void aliasesCanBeLiteralsOfEveryPrimitiveType(Harness h) {
        h.run("""
                #def n = 5
                #def half = 0.5
                #def on = true
                set_level value_of(n)
                set_level value_of(n + 1)
                set_level value_of(half * 4)
                if on log yes
                if n > 4 log big
                log value_of(n + 1 as_string)
                """);
        h.assertCalls(call("set_level", 5), call("set_level", 6), call("set_level", 2), call("log", "yes"),
                call("log", "big"), call("log", "6"));
    }

    @EngineTest
    void aBareWordIsAStringAliasUnlessItNamesAGetterOrAlias(Harness h) {
        h.world.message = "m";
        h.run("""
                #def word = asdf
                #def same = word
                #def msg = message
                log value_of(word)
                log value_of(word + "!")
                log value_of(same)
                log value_of(msg)
                """);
        h.assertCalls(call("log", "asdf"), call("log", "asdf!"), call("log", "asdf"), call("log", "m"));
    }

    @EngineTest
    void aGetterIsTriedBeforeALiteralOfTheSameText(Harness h) {
        // Getters whose names would otherwise read as a boolean and as a bare-word string, giving
        // what neither literal would.
        Harness other = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.getter("false", BuiltInTypes.BOOLEAN, context -> true));
            registrar.register(ScriptNodes.getter("asdf", BuiltInTypes.STRING, context -> "from the getter"));
        });
        other.run("""
                #def flag = false
                #def word = asdf
                if flag log getter
                log value_of(word)
                """);
        other.assertCalls(call("log", "getter"), call("log", "from the getter"));
    }

    @EngineTest
    void aPreviousAliasIsTriedBeforeALiteralOfTheSameText(Harness h) {
        h.world.level = 3;
        h.run("""
                #def true = 5
                #def asdf = level
                #def copied = true
                #def read = asdf
                set_level value_of(read)
                set_level value_of(copied)
                set_level value_of(copied + 1)
                """);
        // read first: set_level changes the level it reads.
        h.assertCalls(call("set_level", 3), call("set_level", 5), call("set_level", 6));
        Map<String, TypeKey> types = new HashMap<>();
        for (PreProcessorToken token : h.prepare("""
                #def true = 5
                #def asdf = level
                #def copied = true
                #def read = asdf
                """).tokens()) {
            types.put(token.text(), token.valueType());
        }
        assertEquals(BuiltInTypes.INT.key(), types.get("copied"));
        assertEquals(BuiltInTypes.INT.key(), types.get("read"));
    }

    @EngineTest
    void textNoPrimitiveTypeReadsIsNotALiteralAlias(Harness h) {
        assertEquals(List.of(
                        PreProcessDiagnostic.atLine(0,
                                "Alias must be a literal, or an expression that starts with a getter or previous alias"),
                        PreProcessDiagnostic.atLine(1,
                                "Alias must be a literal, or an expression that starts with a getter or previous alias"),
                        PreProcessDiagnostic.atLine(2,
                                "Alias must be a literal, or an expression that starts with a getter or previous alias")),
                h.diagnostics("""
                        #def path = target/site
                        #def two = "a" "b"
                        #def open = "never closed
                        """));
    }

    @EngineTest
    void anAliasMayShareAnExecutorsName(Harness h) {
        // It only stands where an expression starts, which no executor does.
        h.run("""
                #def log = "x"
                log value_of(log + "y")
                """);
        h.assertCalls(call("log", "xy"));
    }

    @EngineTest
    void literalAliasesAreOfferedAndHighlightedOnlyWhereExpressionsStart(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare("""
                #def greeting = "hello"
                #def n = 5
                """);
        Map<String, PreProcessorToken> tokens = new HashMap<>();
        prepared.tokens().forEach(token -> tokens.put(token.text(), token));
        assertEquals(new PreProcessorToken("greeting", PreProcessorToken.Placement.EXPRESSION,
                BuiltInTypes.STRING.key(), "\"hello\""), tokens.get("greeting"));
        assertEquals(BuiltInTypes.INT.key(), tokens.get("n").valueType());
        assertEquals(2, prepared.tokens().size());

        assertFalse(h.suggest("log gr", prepared).contains("greeting"));
        assertTrue(h.suggest("log value_of(gr", prepared).contains("greeting"));
        assertEquals(List.of("EXECUTOR:log", "ARGUMENT:greeting"),
                HighlightTest.highlights(h.engine, h, "log greeting", prepared));
        assertEquals(List.of("EXECUTOR:log", "ARGUMENT:value_of(", "ALIAS:greeting", "MAPPER:+", "ARGUMENT:\"x\"",
                        "ARGUMENT:)"),
                HighlightTest.highlights(h.engine, h, "log value_of(greeting + \"x\")", prepared));
    }
}
