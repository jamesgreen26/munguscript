package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.preprocess.ExpressionProbe;
import g_mungus.munguscript.language.builtin.BuiltInTypes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A view over a tree found again where it was grafted, as a client would have it. */
class ViewTest {

    private static ScriptView<TestHost.Source> viewOf(Harness h) {
        return h.provider.view(TestHost.INSTANCE,
                List.of(TestTypes.POINT, TestTypes.COLOR, TestTypes.CELSIUS, TestTypes.COUNTER), h.engine.restrictions(),
                h.grafted);
    }

    @EngineTest
    void aViewOverTheEnginesOwnTreeHasTheEnginesScriptRoot(Harness h) {
        assertSame(h.engine.scriptRoot(), viewOf(h).scriptRoot());
    }

    @EngineTest
    void aViewParsesCommandsWithoutRunningThem(Harness h) {
        ScriptView<TestHost.Source> view = viewOf(h);
        assertTrue(view.parse("paint red", h.source()).getExceptions().isEmpty());
        assertFalse(view.parse("paint red", h.source()).getReader().canRead());
        assertFalse(view.parse("set_level value_of(level crash)", h.source()).getReader().canRead());
        h.assertCalls();
    }

    @EngineTest
    void aViewProbesExpressions(Harness h) {
        ExpressionProbe probe = viewOf(h).probe(h.source());
        assertTrue(probe.readsAs("level is_positive", BuiltInTypes.BOOLEAN.key()));
        assertFalse(probe.readsAs("level", BuiltInTypes.BOOLEAN.key()));
        assertTrue(probe.readsAs("here plus 1 1", TestTypes.POINT.key()));
        assertFalse(probe.readsAs("here plus", TestTypes.POINT.key()));
        assertFalse(probe.readsAs("frobnicate", BuiltInTypes.INT.key()));
    }

    @EngineTest
    void aViewKnowsTheTypesItWasGiven(Harness h) {
        ScriptView<TestHost.Source> view = viewOf(h);
        assertEquals("test:point", view.typeName(TestTypes.POINT.key()));
        assertEquals("int", view.typeName(BuiltInTypes.INT.key()));
        assertTrue(view.type(TestTypes.COLOR.key()).isPresent());
        assertTrue(view.type(BuiltInTypes.STRING.key()).isPresent());
    }

    @EngineTest
    void aViewSuggests(Harness h) {
        ScriptView<TestHost.Source> view = viewOf(h);
        List<String> suggestions = view.suggest("move value_of(he", 16, h.source(), null).join().getList().stream()
                .map(suggestion -> suggestion.getText()).toList();
        assertTrue(suggestions.contains("here"), suggestions.toString());
    }

    @EngineTest
    void aViewOnlySuggestsRestrictedNodesWhereTheyApply(Harness h) {
        Harness r = RestrictionTest.restricted(h);
        ScriptView<TestHost.Source> view = viewOf(r);
        assertFalse(suggest(r, view, "mi").contains("mine"));
        r.block = "block_a";
        assertTrue(suggest(r, view, "mi").contains("mine"));
    }

    private static List<String> suggest(Harness h, ScriptView<TestHost.Source> view, String command) {
        return view.suggest(command, command.length(), h.source(), null).join().getList().stream()
                .map(suggestion -> suggestion.getText()).toList();
    }

    @EngineTest
    void aViewsAliasesKnowTheGettersAndMappers(Harness h) {
        ScriptView<TestHost.Source> view = h.provider.view(TestHost.INSTANCE, List.of(), List.of(), h.grafted);
        var diagnostics = view.aliases().prepare(List.of("#def scale = level", "#def ok = level"),
                h.preProcessContext()).diagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals("Alias name 'scale' conflicts with a mapper", diagnostics.get(0).message());
    }
}
