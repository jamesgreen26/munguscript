package g_mungus.munguscript.conformance;

import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptMapper;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.DOUBLE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a build is made of, and what it refuses. */
class RegistrationTest {

    private static List<String> mapperNamesFrom(Harness h, String inputPath) {
        return h.engine.nodes().stream()
                .filter(node -> node instanceof ScriptMapper<?, ?> mapper && mapper.inputType().key().path().equals(inputPath)
                        || node instanceof ScriptArgumentMapper<?, ?, ?> argumentMapper
                        && argumentMapper.inputType().key().path().equals(inputPath))
                .map(ScriptNode::displayName)
                .toList();
    }

    @EngineTest
    void theEngineHasTheBuiltInTypesAndTheHostsOwn(Harness h) {
        Set<ScriptType<?>> types = Set.copyOf(h.engine.types());
        assertTrue(types.containsAll(List.of(INT, DOUBLE, STRING, BOOLEAN)), types.toString());
        assertTrue(types.containsAll(List.of(TestTypes.POINT, TestTypes.COLOR, TestTypes.CELSIUS, TestTypes.COUNTER)));
        assertEquals(TestTypes.POINT, h.engine.type(TestTypes.POINT.key()).orElseThrow());
    }

    @EngineTest
    void theNodesIncludeTheHostsBuiltInsAndGenerated(Harness h) {
        Set<String> names = h.engine.nodes().stream().map(ScriptNode::displayName).collect(Collectors.toSet());
        assertTrue(names.containsAll(List.of("here", "plus", "paint", "+", "&&", "as_string", "as_point", "==")),
                names.toString());
    }

    @EngineTest
    void writableTypesGetGeneratedMappersButOpaqueOnesDoNot(Harness h) {
        assertTrue(mapperNamesFrom(h, "point").containsAll(List.of("as_string", "==")));
        assertTrue(mapperNamesFrom(h, "string").contains("as_point"));
        assertEquals(List.of("value"), mapperNamesFrom(h, "counter"));
    }

    @EngineTest
    void aRegisteredMapperWinsOverAGeneratedOne(Harness h) {
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.argumentMapper("==", TestTypes.COLOR, BOOLEAN, TestTypes.COLOR,
                    (color, context) -> true));
        });
        assertEquals(1, custom.engine.nodes().stream()
                .filter(node -> node.displayName().equals("==") && node instanceof ScriptArgumentMapper<?, ?, ?> mapper
                        && mapper.inputType() == TestTypes.COLOR)
                .count());
        assertEquals(true, custom.valueOf("favourite == blue", BOOLEAN));
    }

    @EngineTest
    void aNodeWithAnUnregisteredTypeIsRefused(Harness h) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> h.with((registrar, world) ->
                registrar.register(ScriptNodes.getter("ghost", ScriptType.opaque(TestTypes.key("ghost"), Object.class),
                        context -> null))));
        assertTrue(e.getMessage().contains("'ghost' uses test:ghost"), e.getMessage());
    }

    @EngineTest
    void aTypeKeyCanOnlyBeRegisteredOnce(Harness h) {
        assertThrows(IllegalStateException.class, () -> h.with((registrar, world) -> {
            registrar.registerType(TestTypes.POINT);
            registrar.registerType(TestTypes.POINT);
        }));
    }

    @EngineTest
    void anEngineWithNothingButTheBuiltInsStillWorks(Harness h) {
        Harness bare = h.with((registrar, world) -> registrar.register(ScriptNodes.executor("note", INT,
                (value, context) -> world.record("note", value))));
        bare.run("note 6");
        bare.assertCalls(call("note", 6));
        assertEquals("'level' is not a known value in value_of(level)", bare.failure("note value_of(level)").reason());
    }

    @EngineTest
    void aNodeFunctionGetsTheHostContextAndItsArgument(Harness h) {
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.argumentMapper("offset", INT, INT, INT,
                    (value, context) -> value + context.argumentValue(int.class)
                            + context.host(TestHost.Place.class).pos().y()));
            registrar.register(ScriptNodes.mapper("no_argument", INT, INT,
                    (value, context) -> context.argumentValue(Integer.class)));
        });
        assertEquals(1 + 2 + 8, custom.valueOf("level + 1 offset 2", INT));
        assertTrue(custom.failure("set_level value_of(level no_argument)").reason().startsWith(
                "Could not evaluate 'no_argument'"));
    }

    @EngineTest
    void enginesBuiltSeparatelyDoNotShareAnything(Harness h) {
        Harness other = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.executor("extra", INT, (value, context) -> world.record("extra", value)));
        });
        other.run("extra 1");
        h.failure("extra 1");
        h.world.level = 5;
        assertEquals(0, other.valueOf("level", INT));
        assertEquals(5, h.valueOf("level", INT));
        other.assertCalls(call("extra", 1));
        h.assertCalls();
    }
}
