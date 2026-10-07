package g_mungus.munguscript.engine.preprocess;

import com.mojang.brigadier.context.StringRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SourceMapTest {

    private static String original(String input, Rewritten rewritten, String part) {
        int start = rewritten.text().indexOf(part);
        return rewritten.map().toOriginal(StringRange.between(start, start + part.length())).get(input);
    }

    @Test
    void keptTextMapsToItselfAndReplacedTextToTheWholeWord() {
        String input = "move @base now";
        Rewritten rewritten = new Rewriter(input).keep(0, 5).replace(5, 10, "3 4").keep(10, input.length()).build();
        assertEquals("move 3 4 now", rewritten.text());
        assertEquals("move", original(input, rewritten, "move"));
        assertEquals("@base", original(input, rewritten, "4"));
        assertEquals("@base", original(input, rewritten, "3 4"));
        assertEquals("now", original(input, rewritten, "now"));
        assertEquals("@base now", original(input, rewritten, "4 now"));
    }

    @Test
    void rewritesComposeBackToTheFirstInput() {
        String input = "a b";
        Rewritten first = new Rewriter(input).keep(0, 2).replace(2, 3, "x y").build();
        Rewritten second = new Rewriter(first.text()).replace(0, 1, "long").keep(1, first.text().length()).build();
        Rewritten both = first.then(second);
        assertEquals("long x y", both.text());
        assertEquals("a", original(input, both, "long"));
        assertEquals("b", original(input, both, "y"));
    }

    @Test
    void nestedRewritesKeepTheirOwnMaps() {
        String inner = "spot x";
        Rewritten innerRewrite = new Rewriter(inner).replace(0, 4, "here plus 1 1").keep(4, inner.length()).build();
        String input = "value_of(spot x)";
        Rewritten outer = new Rewriter(input).keep(0, 9).rewrite(9, 15, innerRewrite).keep(15, input.length()).build();
        assertEquals("value_of(here plus 1 1 x)", outer.text());
        assertEquals("spot", original(input, outer, "plus"));
        assertEquals("x", original(input, outer, " x").strip());
    }

    @Test
    void anUnchangedRewriteIsTheIdentity() {
        assertSame(SourceMap.IDENTITY, new Rewriter("abc").keep(0, 3).build().map());
    }
}
