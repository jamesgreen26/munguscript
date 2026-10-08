package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What each word of a command is, for syntax highlighting. */
class HighlightTest {
    private static final String ALIASES = """
            #def spot = here
            #def hot = level to_celsius warm
            """;

    /** Each highlight as {@code KIND:text}, which reads like the command. */
    static List<String> highlights(ScriptView<TestHost.Source> view, Harness h, String command,
                                   CommandPreProcessor.@Nullable Prepared prepared) {
        return view.highlight(command, h.source(), prepared).stream()
                .map(highlight -> highlight.kind() + ":" + highlight.range().get(command))
                .toList();
    }

    private static List<String> highlights(Harness h, String command) {
        return highlights(h.engine, h, command, null);
    }

    @EngineTest
    void executorsAndTheirArguments(Harness h) {
        assertEquals(List.of("EXECUTOR:paint", "ARGUMENT:red"), highlights(h, "paint red"));
        assertEquals(List.of("EXECUTOR:move", "ARGUMENT:~1 2"), highlights(h, "move ~1 2"));
    }

    @EngineTest
    void conditionsAndBranches(Harness h) {
        assertEquals(List.of("KEYWORD:if", "GETTER:level", "MAPPER:>", "ARGUMENT:5", "EXECUTOR:paint",
                        "ARGUMENT:red", "KEYWORD:else", "KEYWORD:unless", "GETTER:here", "MAPPER:x",
                        "MAPPER:is_positive", "EXECUTOR:paint", "ARGUMENT:blue"),
                highlights(h, "if level > 5 paint red else unless here x is_positive paint blue"));
    }

    @EngineTest
    void onlyTheBracketsOfAValueOfAreAnArgument(Harness h) {
        assertEquals(List.of("EXECUTOR:set_level", "ARGUMENT:value_of(", "GETTER:level", "MAPPER:scale",
                        "ARGUMENT:value_of(", "GETTER:level", "ARGUMENT:)", "ARGUMENT:)"),
                highlights(h, "set_level value_of(level scale value_of(level))"));
        assertEquals(List.of("EXECUTOR:move", "ARGUMENT:value_of(", "GETTER:here", "MAPPER:plus", "ARGUMENT:1 2",
                        "ARGUMENT:)"),
                highlights(h, "move value_of(here plus 1 2)"));
    }

    @EngineTest
    void aValueOfBeingWrittenIsHighlightedAsFarAsItReads(Harness h) {
        assertEquals(List.of("EXECUTOR:set_level", "ARGUMENT:value_of(", "GETTER:level", "MAPPER:scale"),
                highlights(h, "set_level value_of(level scale"));
        assertEquals(List.of("EXECUTOR:set_level", "ARGUMENT:value_of(", "UNPARSED:lev"),
                highlights(h, "set_level value_of(lev"));
        // Of the wrong type: still highlighted as written; the error comes from parsing.
        assertEquals(List.of("EXECUTOR:set_level", "ARGUMENT:value_of(", "GETTER:here", "ARGUMENT:)"),
                highlights(h, "set_level value_of(here)"));
    }

    @EngineTest
    void whatCannotBeReadIsUnparsed(Harness h) {
        assertEquals(List.of("UNPARSED:frobnicate now"), highlights(h, "frobnicate now"));
        assertEquals(List.of("EXECUTOR:paint", "ARGUMENT:red", "UNPARSED:junk"), highlights(h, "paint red junk"));
        assertEquals(List.of("EXECUTOR:paint", "UNPARSED:purple"), highlights(h, "paint purple"));
    }

    @EngineTest
    void aliasesAndAddressesAreHighlightedAsWritten(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        assertEquals(List.of("KEYWORD:if", "ALIAS:hot", "EXECUTOR:paint", "ARGUMENT:red"),
                highlights(h.engine, h, "if hot paint red", prepared));
        assertEquals(List.of("EXECUTOR:move", "ARGUMENT:value_of(", "ALIAS:spot", "MAPPER:plus", "ARGUMENT:@base",
                        "ARGUMENT:)"),
                highlights(h.engine, h, "move value_of(spot plus @base)", prepared));
    }

    @EngineTest
    void anArgumentWrittenLikeAnAliasIsStillAnArgument(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare("#def blue = favourite\n");
        assertEquals(List.of("EXECUTOR:paint", "ARGUMENT:blue"), highlights(h.engine, h, "paint blue", prepared));
    }

    @EngineTest
    void wordsAValueHasThroughAConversionAreMappers(Harness h) {
        assertEquals(List.of("KEYWORD:if", "GETTER:level", "MAPPER:to_celsius", "MAPPER:>", "ARGUMENT:20.5",
                        "EXECUTOR:paint", "ARGUMENT:red"),
                highlights(h, "if level to_celsius > 20.5 paint red"));
    }

    static List<String> expressionHighlights(ScriptView<TestHost.Source> view, Harness h, String expression,
                                             CommandPreProcessor.@Nullable Prepared prepared) {
        return view.highlightExpression(expression, h.source(), prepared).stream()
                .map(highlight -> highlight.kind() + ":" + highlight.range().get(expression))
                .toList();
    }

    @EngineTest
    void anExpressionOnItsOwn(Harness h) {
        assertEquals(List.of("GETTER:level", "MAPPER:scale", "ARGUMENT:value_of(", "GETTER:level", "ARGUMENT:)"),
                expressionHighlights(h.engine, h, "level scale value_of(level)", null));
        assertEquals(List.of("GETTER:here", "MAPPER:plus", "ARGUMENT:1 2", "MAPPER:x"),
                expressionHighlights(h.engine, h, "here plus 1 2 x", null));
        assertEquals(List.of("GETTER:level", "UNPARSED:sc"), expressionHighlights(h.engine, h, "level sc", null));
        assertEquals(List.of("UNPARSED:frob"), expressionHighlights(h.engine, h, "frob", null));
    }

    @EngineTest
    void anExpressionWithAliasesAndAddresses(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        CommandPreProcessor.Prepared prepared = h.prepare(ALIASES);
        assertEquals(List.of("ALIAS:spot", "MAPPER:plus", "ARGUMENT:@base", "MAPPER:x"),
                expressionHighlights(h.engine, h, "spot plus @base x", prepared));
        assertEquals(List.of("ALIAS:hot", "MAPPER:not"), expressionHighlights(h.engine, h, "hot not", prepared));
        assertEquals(List.of("GETTER:here", "MAPPER:plus", "ARGUMENT:value_of(", "ALIAS:spot"),
                expressionHighlights(h.engine, h, "here plus value_of(spot", prepared));
    }

    @EngineTest
    void eachHighlightIsAKindTheEngineNames(Harness h) {
        // Every kind but ALIAS shows up in one command; ALIAS is covered above.
        List<Highlight.Kind> kinds = h.engine.highlight("if level > 5 paint value_of(here x) else frob", h.source(), null)
                .stream().map(Highlight::kind).distinct().sorted().toList();
        assertEquals(List.of(Highlight.Kind.KEYWORD, Highlight.Kind.EXECUTOR, Highlight.Kind.GETTER,
                Highlight.Kind.MAPPER, Highlight.Kind.ARGUMENT, Highlight.Kind.UNPARSED), kinds);
    }
}
