package g_mungus.munguscript.conformance;

import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.language.builtin.BuiltInTypes;

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
