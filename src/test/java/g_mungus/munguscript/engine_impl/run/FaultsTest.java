package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.host.Match;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaultsTest {

    @Test
    void aFaultIsFoundInTheCommandBehindTheHostsPrefix() {
        ScriptFault fault = new ScriptFault("bad", "script set x", StringRange.between(11, 12));
        assertEquals(Optional.of(StringRange.between(4, 5)), fault.rangeIn("set x"));
        assertEquals(Optional.empty(), fault.rangeIn("other"));
    }

    @Test
    void aFaultFromAnInnerRunMovesOutToTheWholeCommand() {
        ScriptFault inner = new ScriptFault("bad", "run set value_of(a", StringRange.between(17, 18));
        assertEquals(Optional.of(StringRange.between(13, 14)), inner.within("run set value_of(a)").rangeIn("set value_of(a)"));
    }

    @Test
    void aReasonIsTheFirstMessageSomeoneWrote() {
        assertEquals("the real reason", Reasons.of(new RuntimeException(new IllegalStateException("the real reason"))));
        assertEquals("could not write", Reasons.of(new UncheckedIOException("could not write", new IOException("disk"))));
        assertEquals(Reasons.UNKNOWN, Reasons.of(new RuntimeException()));
    }

    @Test
    void theOverloadRulePrefersAnExplicitMatchThenTheFirstUnrestricted() {
        var answers = List.of(Match.NONE, Match.UNRESTRICTED,
                Match.UNRESTRICTED, Match.EXPLICIT);
        assertEquals(3, Overloads.choose(List.of(0, 1, 2, 3), answers::get));
        assertEquals(1, Overloads.choose(List.of(0, 1, 2), answers::get));
        assertEquals(-1, Overloads.choose(List.of(0), answers::get));
    }
}
