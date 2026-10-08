package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.ScriptType;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.DOUBLE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Types usable as others, without a word in the script: those a type declares, and a string. */
class ConversionTest {

    private static ScriptType<Object> opaque(String path) {
        return ScriptType.opaque(TestTypes.key(path), Object.class);
    }

    @EngineTest
    void aValueIsUsableWhereATypeItConvertsToIsWanted(Harness h) {
        h.world.level = 30;
        assertEquals(30.0, h.valueOf("level to_celsius", DOUBLE));
    }

    @EngineTest
    void theMappersOfATypeItIsUsableAsCanFollowAValue(Harness h) {
        h.world.level = 30;
        assertEquals(30, h.valueOf("level to_celsius rounded_down", INT));
        assertEquals(true, h.valueOf("level to_celsius > 20.5", BOOLEAN));
        assertEquals("red!", h.valueOf("favourite + \"!\"", STRING));
        h.run("if level to_celsius > 20.5 log \"warm\"");
        h.assertCalls(call("log", "warm"));
    }

    @EngineTest
    void aTypesOwnMapperWinsOverOneOfTheSameName(Harness h) {
        h.world.level = 4;
        assertEquals(5, h.valueOf("level + 1", INT));
        assertFalse(h.parsesFully("log value_of(level + \"x\")"));
    }

    @EngineTest
    void everyTypeIsUsableAsAString(Harness h) {
        h.world.level = 4;
        h.world.favourite = Color.GREEN;
        h.run("log value_of(favourite)\nlog value_of(level)\nlog value_of(here)\nlog value_of(level to_celsius)");
        h.assertCalls(call("log", "green"), call("log", "4"), call("log", "7 8"), call("log", "4.0"));
        assertEquals(String.valueOf(h.world.counter), h.valueOf("counter", STRING));
    }

    @EngineTest
    void aStringIsOnlyTheLastResortAmongOverloads(Harness h) {
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.executor("show", STRING, (text, context) -> world.record("show", text)));
            registrar.register(ScriptNodes.executor("show", DOUBLE, (value, context) -> world.record("show", value)));
        });
        custom.world.level = 4;
        custom.world.favourite = Color.BLUE;
        custom.run("show value_of(level to_celsius)\nshow value_of(favourite)");
        custom.assertCalls(call("show", 4.0), call("show", "blue"));
    }

    @EngineTest
    void aTypeCannotBeUsableAsOneThatIsNotRegistered(Harness h) {
        ScriptType<Object> haunted = opaque("haunted").usableAs(opaque("ghost"), value -> value);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> h.with((registrar, world) -> registrar.registerType(haunted)));
        assertTrue(e.getMessage().contains("test:haunted is usable as test:ghost, which is not registered"),
                e.getMessage());
    }

    @EngineTest
    void aTypeCannotBeUsableAsAnotherInTwoWays(Harness h) {
        ScriptType<Object> top = opaque("top");
        ScriptType<Object> left = opaque("left").usableAs(top, value -> value);
        ScriptType<Object> right = opaque("right").usableAs(top, value -> value);
        ScriptType<Object> bottom = opaque("bottom").usableAs(left, value -> value).usableAs(right, value -> value);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> h.with((registrar, world) -> {
            registrar.registerType(top);
            registrar.registerType(left);
            registrar.registerType(right);
            registrar.registerType(bottom);
        }));
        assertTrue(e.getMessage().contains("test:bottom is usable as test:top in two ways"), e.getMessage());
    }
}
