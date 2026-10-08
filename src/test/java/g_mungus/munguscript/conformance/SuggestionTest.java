package g_mungus.munguscript.conformance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the engine suggests while a script is being written. */
class SuggestionTest {
    private static final String ALIASES = """
            #def spot = here
            #def hot = level to_celsius warm
            """;

    @EngineTest
    void commandsAndArgumentsAreSuggested(Harness h) {
        assertTrue(h.suggest("pa", null).contains("paint"));
        assertTrue(h.suggest("paint b", null).contains("blue"));
        assertTrue(h.suggest("paint v", null).contains("value_of("));
        assertTrue(h.suggest("", null).containsAll(List.of("paint", "move", "if", "unless")));
    }

    @EngineTest
    void valueOfIsOnlySuggestedAloneOnceItHasBeenStarted(Harness h) {
        // Nothing else to suggest for an int: an empty list leaves the client showing the slot's hint.
        assertEquals(List.of(), h.suggest("set_level ", null));
        assertTrue(h.suggest("set_level v", null).contains("value_of("));
        assertTrue(h.suggest("paint ", null).containsAll(List.of("red", "value_of(")));
    }

    @EngineTest
    void wordsAValueOnlyHasThroughAConversionAreSuggestedOnceStarted(Harness h) {
        // celsius is usable as a double, and everything as a string.
        List<String> afterCelsius = h.suggest("if level to_celsius ", null);
        assertTrue(afterCelsius.contains("warm"), afterCelsius.toString());
        assertFalse(afterCelsius.contains(">"), afterCelsius.toString());
        assertFalse(afterCelsius.contains("lines"), afterCelsius.toString());
        assertTrue(h.suggest("if level to_celsius >", null).contains(">"));
        assertTrue(h.suggest("if level to_celsius r", null).contains("rounded_down"));

        List<String> afterCounter = h.suggest("log value_of(counter ", null);
        assertEquals(List.of("value"), afterCounter);
        assertTrue(h.suggest("log value_of(counter l", null).contains("lines"));
    }

    @EngineTest
    void aHostsOwnDispatcherOnlySuggestsConvertedWordsOnceStarted(Harness h) throws Exception {
        // As a host does that leads its own command into a chain, and lets Brigadier suggest the rest.
        CommandDispatcher<TestHost.Source> host = new CommandDispatcher<>();
        host.register(LiteralArgumentBuilder.<TestHost.Source>literal("temp")
                .redirect(h.grafted.getChild("munguscript:value/test:celsius")));
        List<String> afterTemp = brigadierSuggestions(host, "temp ", h);
        assertTrue(afterTemp.contains("warm"), afterTemp.toString());
        assertFalse(afterTemp.contains(">"), afterTemp.toString());
        assertFalse(afterTemp.contains("lines"), afterTemp.toString());
        assertTrue(brigadierSuggestions(host, "temp r", h).contains("rounded_down"));
        assertTrue(brigadierSuggestions(host, "temp l", h).contains("lines"));
    }

    private static List<String> brigadierSuggestions(CommandDispatcher<TestHost.Source> dispatcher, String command,
                                                     Harness h) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, h.source())).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @EngineTest
    void aWordThatOnlyLeadsOnThroughAConversionIsSuggestedOnceStarted(Harness h) {
        // A reactor has nothing of its own: it only becomes a celsius through a conversion.
        ScriptType<Double> reactor = ScriptType.opaque(TestTypes.key("reactor"), Double.class)
                .usableAs(TestTypes.CELSIUS, degrees -> degrees);
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.registerType(reactor);
            registrar.register(ScriptNodes.getter("core", reactor, context -> 900.0));
            registrar.register(ScriptNodes.executor("vent", TestTypes.CELSIUS, (value, context) -> world.record("vent", value)));
        });
        assertFalse(custom.suggest("vent value_of(", null).contains("core"));
        assertTrue(custom.suggest("vent value_of(c", null).contains("core"));
        assertTrue(custom.parsesFully("vent value_of(core)"));
    }

    static List<String> suggestExpression(ScriptView<TestHost.Source> view, Harness h, String expression,
                                          @Nullable TypeKey type, CommandPreProcessor.@Nullable Prepared prepared) {
        return view.suggestExpression(expression, expression.length(), h.source(), type, prepared).join().getList()
                .stream().map(Suggestion::getText).toList();
    }

    @EngineTest
    void aLoneExpressionIsSuggestedAsInsideAValueOf(Harness h) {
        List<String> start = suggestExpression(h.engine, h, "", null, null);
        assertTrue(start.containsAll(List.of("here", "level", "message", "counter")), start.toString());
        assertFalse(start.contains(")"), start.toString());
        List<String> afterLevel = suggestExpression(h.engine, h, "level ", null, null);
        assertTrue(afterLevel.containsAll(List.of(">", "scale", "to_celsius")), afterLevel.toString());
        assertFalse(afterLevel.contains(")"), afterLevel.toString());
        assertEquals(List.of("level"), suggestExpression(h.engine, h, "lev", null, null));
    }

    @EngineTest
    void aLoneExpressionOnlyOffersWhatLeadsToTheTypeWanted(Harness h) {
        List<String> afterHere = suggestExpression(h.engine, h, "here ", TestTypes.POINT.key(), null);
        assertTrue(afterHere.contains("plus"), afterHere.toString());
        assertFalse(afterHere.contains("x"), afterHere.toString());
        assertTrue(suggestExpression(h.engine, h, "here ", null, null).contains("x"));
    }

    @EngineTest
    void aLoneExpressionsSuggestionsAreLocatedInIt(Harness h) {
        List<Suggestion> suggestions = h.engine.suggestExpression("here pl", 7, h.source(), null, null).join().getList();
        assertEquals(1, suggestions.size(), suggestions.toString());
        assertEquals("plus", suggestions.get(0).getText());
        assertEquals(StringRange.between(5, 7), suggestions.get(0).getRange());
    }

    @EngineTest
    void aCursorOutsideTheTextIsAtItsNearerEnd(Harness h) {
        List<String> atEnd = texts(h.engine.suggest("paint b", 7, h.source(), null).join());
        assertEquals(atEnd, texts(h.engine.suggest("paint b", 100, h.source(), null).join()));
        assertEquals(texts(h.engine.suggest("paint b", 0, h.source(), null).join()),
                texts(h.engine.suggest("paint b", -3, h.source(), null).join()));
        assertEquals(List.of("level"), texts(h.engine.suggestExpression("lev", 100, h.source(), null, null).join()));
        assertEquals(texts(h.engine.suggestExpression("lev", 0, h.source(), null, null).join()),
                texts(h.engine.suggestExpression("lev", -1, h.source(), null, null).join()));
    }

    private static List<String> texts(Suggestions suggestions) {
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    @EngineTest
    void aLoneExpressionOffersAndExpandsAliases(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        List<String> start = suggestExpression(h.engine, h, "", null, prepared);
        assertTrue(start.containsAll(List.of("spot", "hot", "here")), start.toString());
        assertEquals(List.of("spot"), suggestExpression(h.engine, h, "sp", null, prepared));
        List<String> afterSpot = suggestExpression(h.engine, h, "spot ", null, prepared);
        assertTrue(afterSpot.containsAll(List.of("x", "y", "plus")), afterSpot.toString());
        List<String> forPoint = suggestExpression(h.engine, h, "", TestTypes.POINT.key(), prepared);
        assertTrue(forPoint.contains("spot"), forPoint.toString());
        assertFalse(forPoint.contains("hot"), forPoint.toString());
    }

    @EngineTest
    void conditionsAreSuggestedFromGettersThatCanReachABoolean(Harness h) {
        List<String> afterIf = h.suggest("if ", null);
        assertTrue(afterIf.containsAll(List.of("here", "level", "message")), afterIf.toString());
        assertTrue(h.suggest("unless le", null).contains("level"));
        assertTrue(h.suggest("if level ", null).containsAll(List.of(">", "to_celsius", "==")));
    }

    @EngineTest
    void executorsAreSuggestedOnceAConditionIsComplete(Harness h) {
        List<String> suggestions = h.suggest("if level > 5 ", null);
        assertTrue(suggestions.containsAll(List.of("log", "paint", "&&")), suggestions.toString());
        assertTrue(h.suggest("if level > 5 log a ", null).contains("else"));
    }

    @EngineTest
    void valueOfContentsAreSuggested(Harness h) {
        assertTrue(h.suggest("move value_of(he", null).contains("here"));
        assertTrue(h.suggest("move value_of(here ", null).contains("plus"));
        assertTrue(h.suggest("paint value_of(favourite", null).contains(")"));
        assertFalse(h.suggest("set_level value_of(here", null).contains(")"));
        assertTrue(h.suggest("set_level value_of(here x", null).contains(")"));
    }

    @EngineTest
    void suggestionsAreMadeAtTheCursor(Harness h) {
        String command = "paint b";
        List<String> atStart = h.engine.suggest(command, 2, h.source(), null).join().getList().stream()
                .map(Suggestion::getText).toList();
        assertTrue(atStart.contains("paint"), atStart.toString());
    }

    @EngineTest
    void addressesAreOfferedWhereTheirTypeIsWanted(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        CommandPreProcessor.Prepared prepared = h.prepare("");
        assertTrue(h.suggest("move @", prepared).contains("@base"));
        assertFalse(h.suggest("set_level @", prepared).contains("@base"));
    }

    @EngineTest
    void aliasesAreOfferedWhereAnExpressionStarts(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        assertTrue(h.suggest("set_level value_of(sp", prepared).contains("spot"));
        assertTrue(h.suggest("if h", prepared).contains("hot"));
        assertFalse(h.suggest("move s", prepared).contains("spot"));
    }

    @EngineTest
    void mappersAreSuggestedAfterAnAliasInsideValueOf(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        List<String> suggestions = h.suggest("set_level value_of(spot ", prepared);
        assertTrue(suggestions.containsAll(List.of("x", "y")), suggestions.toString());
        assertTrue(h.suggest("set_level value_of(spot x", prepared).contains(")"));
    }

    @EngineTest
    void mappersAndExecutorsAreSuggestedAfterAnAliasInACondition(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        assertTrue(h.suggest("if spot ", prepared).contains("x"));
        List<String> afterHot = h.suggest("if hot ", prepared);
        assertTrue(afterHot.containsAll(List.of("&&", "log", "paint")), afterHot.toString());
    }

    @EngineTest
    void aliasesAreSuggestedAfterElse(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        assertTrue(h.suggest("if level > 5 log a else if ho", prepared).contains("hot"));
        assertTrue(h.suggest("if level > 5 log a else if hot ", prepared).contains("log"));
    }

    @EngineTest
    void anAliasIsSuggestedWithWhatItStandsFor(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        Suggestion hot = h.suggestions("if h", prepared).stream()
                .filter(suggestion -> suggestion.getText().equals("hot"))
                .findFirst().orElseThrow();
        assertEquals("level to_celsius warm", hot.getTooltip().getString());
    }

    @EngineTest
    void suggestingWithAliasesDoesNotTeachTheEngineThem(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        h.suggest("if hot ", prepared);
        var parse = h.engine.parse("if hot log a", h.source());
        assertTrue(parse.getReader().canRead() || !parse.getExceptions().isEmpty());
        assertFalse(h.engine.probe(h.source()).readsAs("spot x", BuiltInTypes.INT.key()));
        assertFalse(h.suggest("if h", null).contains("hot"));
    }
}
