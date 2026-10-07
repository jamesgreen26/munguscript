package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestHost.Blocks;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nodes meant only for some targets are only suggested where the host says they apply. */
class RestrictionTest {

    /** The test nodes, plus restricted ones of every kind. Stone, the harness's default block, has none of them. */
    static Harness restricted(Harness h) {
        return h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.getter("ore", INT, context -> 3).withApplicability(Blocks.of("block_a")));
            registrar.register(ScriptNodes.mapper("hardness", INT, INT, (value, context) -> value * 2)
                    .withApplicability(Blocks.of("block_b")));
            registrar.register(ScriptNodes.argumentMapper("mined", INT, INT, INT,
                    (value, context) -> value - context.argumentValue(Integer.class)).withApplicability(Blocks.of("block_b")));
            registrar.register(ScriptNodes.executor("mine", INT, (value, context) -> world.record("mine", value))
                    .withApplicability(Blocks.of("block_a")));
            registrar.register(ScriptNodes.executor("aim", INT, (value, context) -> world.record("aim_a", value))
                    .withApplicability(Blocks.of("block_a")));
            registrar.register(ScriptNodes.executor("aim", TestTypes.COLOR, (color, context) -> world.record("aim_b", color))
                    .withApplicability(Blocks.of("block_b")));
        });
    }

    @EngineTest
    void theEngineListsItsRestrictedNodesInRegistrationOrder(Harness h) {
        assertEquals(List.of(
                Restriction.executor("configure", 0, Blocks.of("block_a")),
                Restriction.executor("configure", 1, Blocks.of("block_b")),
                Restriction.getter("ore", Blocks.of("block_a")),
                Restriction.mapper("hardness", INT.key(), Blocks.of("block_b")),
                Restriction.mapper("mined", INT.key(), Blocks.of("block_b")),
                Restriction.executor("mine", 0, Blocks.of("block_a")),
                Restriction.executor("aim", 0, Blocks.of("block_a")),
                Restriction.executor("aim", 1, Blocks.of("block_b"))), restricted(h).engine.restrictions());
    }

    @EngineTest
    void anExecutorIsOnlySuggestedWhereItApplies(Harness h) {
        Harness r = restricted(h);
        assertFalse(r.suggest("mi", null).contains("mine"));
        assertFalse(r.suggest("if level > 1 mi", null).contains("mine"));
        assertFalse(r.suggest("if level > 1 log a else mi", null).contains("mine"));
        r.block = "block_a";
        assertTrue(r.suggest("mi", null).contains("mine"));
        assertTrue(r.suggest("if level > 1 mi", null).contains("mine"));
        assertTrue(r.suggest("if level > 1 log a else mi", null).contains("mine"));
    }

    @EngineTest
    void sharedExecutorsAreSuggestedWhereAnyOfThemApplies(Harness h) {
        Harness r = restricted(h);
        assertFalse(r.suggest("ai", null).contains("aim"));
        assertTrue(r.suggest("co", null).contains("configure"), "one configure is meant for any target");
        r.block = "block_a";
        assertTrue(r.suggest("ai", null).contains("aim"));
        r.block = "block_b";
        assertTrue(r.suggest("ai", null).contains("aim"));
    }

    @EngineTest
    void aSharedExecutorsArgumentIsSuggestedOnlyFromThoseThatApply(Harness h) {
        Harness r = restricted(h);
        assertFalse(r.suggest("configure b", null).contains("blue"));
        assertTrue(r.suggest("configure v", null).contains("value_of("));
        r.block = "block_b";
        assertTrue(r.suggest("configure b", null).contains("blue"));
        assertTrue(r.suggest("aim b", null).contains("blue"));
        r.block = "block_a";
        assertFalse(r.suggest("aim b", null).contains("blue"));
    }

    @EngineTest
    void aGetterIsOnlySuggestedWhereItApplies(Harness h) {
        Harness r = restricted(h);
        assertFalse(r.suggest("if o", null).contains("ore"));
        assertFalse(r.suggest("set_level value_of(o", null).contains("ore"));
        r.block = "block_a";
        assertTrue(r.suggest("if o", null).contains("ore"));
        assertTrue(r.suggest("set_level value_of(o", null).contains("ore"));
    }

    @EngineTest
    void mappersAreOnlySuggestedWhereTheyApply(Harness h) {
        Harness r = restricted(h);
        List<String> onStone = r.suggest("set_level value_of(level ", null);
        assertFalse(onStone.contains("hardness") || onStone.contains("mined"), onStone.toString());
        assertTrue(onStone.contains("scale"), onStone.toString());
        r.block = "block_b";
        List<String> onB = r.suggest("set_level value_of(level ", null);
        assertTrue(onB.containsAll(List.of("hardness", "mined", "scale")), onB.toString());
        assertTrue(r.suggest("if level ", null).contains("hardness"));
    }

    @EngineTest
    void restrictionsStillApplyWithAScriptsAliases(Harness h) {
        Harness r = restricted(h);
        CommandPreProcessor.Prepared prepared = r.prepare("#def lvl = level");
        assertFalse(r.suggest("if o", prepared).contains("ore"));
        assertFalse(r.suggest("set_level value_of(lvl ", prepared).contains("hardness"));
        assertTrue(r.suggest("if l", prepared).contains("lvl"));
        r.block = "block_b";
        assertTrue(r.suggest("set_level value_of(lvl ", prepared).contains("hardness"));
    }

    @EngineTest
    void restrictedNodesStillParseAndRunAnywhere(Harness h) {
        Harness r = restricted(h);
        r.run("""
                mine value_of(ore hardness mined 1)
                if ore > 1 mine 2
                """);
        r.assertCalls(call("mine", 5), call("mine", 2));
        assertTrue(r.engine.probe(r.source()).readsAs("ore hardness", INT.key()));
    }
}
