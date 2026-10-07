package g_mungus.munguscript.conformance;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where suggestions apply. Each replaces only the word being written, starting where that word
 * starts, so an editor shows {@code )} rather than {@code favourite)}, and applying any suggestion
 * leaves the rest of the command as it was.
 */
class SuggestionRangeTest {

    /** The suggestion with this text, failing with the whole list if there is none. */
    private static Suggestion find(Harness h, String command, String text, CommandPreProcessor.@Nullable Prepared prepared) {
        List<Suggestion> suggestions = h.suggestions(command, prepared);
        return suggestions.stream().filter(suggestion -> suggestion.getText().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("No '" + text + "' for '" + command + "' in " + suggestions));
    }

    private static void assertAt(Harness h, String command, String text, int start,
                                 CommandPreProcessor.@Nullable Prepared prepared) {
        Suggestion suggestion = find(h, command, text, prepared);
        assertEquals(StringRange.between(start, command.length()), suggestion.getRange(), command + " -> " + text);
        assertEquals(command.substring(0, start) + text, suggestion.apply(command), command + " -> " + text);
    }

    @EngineTest
    void aClosingParenthesisIsSuggestedOnItsOwnAtTheCursor(Harness h) {
        String paint = "paint value_of(favourite";
        assertAt(h, paint, ")", paint.length(), null);
        String level = "set_level value_of(here x";
        assertAt(h, level, ")", level.length(), null);
        String nested = "move value_of(here plus value_of(origin";
        assertAt(h, nested, ")", nested.length(), null);
    }

    @EngineTest
    void aWordIsReplacedFromWhereItStarts(Harness h) {
        assertAt(h, "pa", "paint", 0, null);
        assertAt(h, "paint b", "blue", 6, null);
        assertAt(h, "paint v", "value_of(", 6, null);
        assertAt(h, "move value_of(he", "here", 14, null);
        assertAt(h, "move value_of(here pl", "plus", 19, null);
        assertAt(h, "if level > 5 l", "log", 13, null);
    }

    @EngineTest
    void tokensAreReplacedFromWhereTheyStart(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        CommandPreProcessor.Prepared prepared = h.prepare("#def spot = here");
        assertAt(h, "move @", "@base", 5, prepared);
        assertAt(h, "move value_of(sp", "spot", 14, prepared);
        assertAt(h, "if sp", "spot", 3, prepared);
    }

    @EngineTest
    void everySuggestionLeavesWhatCameBeforeItsWordAlone(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        CommandPreProcessor.Prepared prepared = h.prepare("#def spot = here");
        for (String command : List.of("paint value_of(favourite", "move value_of(here plus 1 1", "move @",
                "if level > value_of(here ", "configure ", "move value_of(sp")) {
            int wordStart = Math.max(command.lastIndexOf(' '), command.lastIndexOf('(')) + 1;
            for (Suggestion suggestion : h.suggestions(command, prepared)) {
                assertTrue(suggestion.getRange().getStart() >= wordStart,
                        "'" + suggestion.getText() + "' for '" + command + "' starts at " + suggestion.getRange());
            }
        }
    }
}
