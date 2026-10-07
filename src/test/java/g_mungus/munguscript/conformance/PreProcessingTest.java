package g_mungus.munguscript.conformance;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;

import java.util.List;
import java.util.Map;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A host pre-processor ({@code @name} addresses) chained after the engine's aliases. */
class PreProcessingTest {

    @EngineTest
    void anAddressBecomesItsPoint(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        h.run("""
                move @base
                move value_of(origin plus @base)
                """);
        h.assertCalls(call("move", new Point(3, 4)), call("move", new Point(3, 4)));
    }

    @EngineTest
    void anAliasBodyCanUseAnAddress(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        h.run("""
                #def near = origin plus @base
                move value_of(near plus 1 1)
                """);
        h.assertCalls(call("move", new Point(4, 5)));
    }

    @EngineTest
    void anUnknownAddressIsReportedWhereItWasWrittenAndTheScriptStops(Harness h) {
        Harness.Outcome outcome = h.execute("""
                log "before"
                move @nowhere
                log "after"
                """);
        assertEquals(1, outcome.line());
        PreProcessDiagnostic diagnostic = outcome.diagnostics().get(0);
        assertEquals("Unknown address @nowhere", diagnostic.message());
        assertEquals("@nowhere", diagnostic.range().get("move @nowhere"));
        h.assertCalls(call("log", "before"));
    }

    @EngineTest
    void aProblemFoundAfterAnAliasIsPointedAtInThePlayersText(Harness h) {
        PreProcessDiagnostic diagnostic = h.diagnostics("""
                #def near = origin plus @nowhere
                move value_of(near)
                """).get(0);
        assertEquals("Unknown address @nowhere", diagnostic.message());
        assertEquals(StringRange.between(14, 18), diagnostic.range());
    }

    @EngineTest
    void aFailureCausedByAnAddressPointsAtTheAddress(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        ScriptFailure failure = h.failure("set_level @base");
        assertEquals("set_level 3 4", failure.executedCommand());
        assertEquals("@base", failure.faultText());
    }

    @EngineTest
    void addressesAreOfferedAsTokens(Harness h) {
        h.addresses = Map.of("base", new Point(3, 4));
        assertEquals(List.of("@base"), h.prepare("").tokens().stream().map(token -> token.text()).toList());
    }
}
