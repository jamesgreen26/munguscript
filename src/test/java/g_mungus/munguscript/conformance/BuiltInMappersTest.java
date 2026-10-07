package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptNodes;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.DOUBLE;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The mappers every engine has, and those generated for every writable type. */
class BuiltInMappersTest {

    @EngineTest
    void intArithmeticAndComparison(Harness h) {
        h.world.level = 7;
        assertEquals(10, h.valueOf("level + 3", INT));
        assertEquals(-3, h.valueOf("level - 10", INT));
        assertEquals(3, h.valueOf("level & 3", INT));
        assertEquals(15, h.valueOf("level | 8", INT));
        assertEquals(28, h.valueOf("level << 2", INT));
        assertEquals(3, h.valueOf("level >> 1", INT));
        assertEquals(3, h.valueOf("level % 4", INT));
        assertEquals(true, h.valueOf("level > 5", BOOLEAN));
        assertEquals(false, h.valueOf("level < 5", BOOLEAN));
        assertEquals(true, h.valueOf("level == 7", BOOLEAN));
    }

    @EngineTest
    void mappersApplyLeftToRight(Harness h) {
        h.world.level = 7;
        assertEquals(20, h.valueOf("level + 3 << 1", INT));
        assertEquals(1, h.valueOf("level - 1 % 5", INT));
    }

    @EngineTest
    void multiplyingOrDividingAnIntGivesADouble(Harness h) {
        h.world.level = 7;
        assertEquals(3.5, h.valueOf("level * 0.5", DOUBLE));
        assertEquals(3.5, h.valueOf("level / 2", DOUBLE));
    }

    @EngineTest
    void doubleArithmeticComparisonAndRounding(Harness h) {
        h.world.level = 7;
        assertEquals(10.75, h.valueOf("level * 1.5 + 0.25", DOUBLE));
        assertEquals(6.0, h.valueOf("level * 1 - 1", DOUBLE));
        assertEquals(14.0, h.valueOf("level / 1 * 2", DOUBLE));
        assertEquals(1.75, h.valueOf("level / 1 / 4", DOUBLE));
        assertEquals(3, h.valueOf("level / 2 rounded_down", INT));
        assertEquals(4, h.valueOf("level / 2 rounded_up", INT));
        assertEquals(true, h.valueOf("level * 1 > 6.5", BOOLEAN));
        assertEquals(false, h.valueOf("level * 1 < 6.5", BOOLEAN));
        assertEquals(true, h.valueOf("level * 1 == 7", BOOLEAN));
    }

    @EngineTest
    void stringsJoinAndSplitIntoLines(Harness h) {
        h.world.message = "a\\nb\\nc";
        assertEquals(3, h.valueOf("message lines", INT));
        assertEquals("b", h.valueOf("message get_line 2", STRING));
        assertEquals("", h.valueOf("message get_line 9", STRING));
        assertEquals("a\\nc", h.valueOf("message remove_line 2", STRING));
        assertEquals("a\\nb\\nc", h.valueOf("message remove_line 0", STRING));
        assertEquals("a\\nb\\nc!", h.valueOf("message + \"!\"", STRING));
        assertEquals(">a\\nb\\nc", h.valueOf("message <+ \">\"", STRING));
        h.world.message = "x,y";
        assertEquals("x\\ny", h.valueOf("message split \",\"", STRING));
        assertEquals("x,y", h.valueOf("message split \"\"", STRING));
        assertEquals(true, h.valueOf("message == \"x,y\"", BOOLEAN));
        assertEquals(1, h.valueOf("message + \"\" lines", INT));
    }

    @EngineTest
    void booleanLogic(Harness h) {
        h.world.level = 7;
        assertEquals(true, h.valueOf("level > 5 && value_of(level < 10)", BOOLEAN));
        assertEquals(false, h.valueOf("level > 5 && false", BOOLEAN));
        assertEquals(true, h.valueOf("level > 9 || value_of(level < 10)", BOOLEAN));
        assertEquals(false, h.valueOf("level > 9 || false", BOOLEAN));
        assertEquals(true, h.valueOf("level > 9 == false", BOOLEAN));
    }

    @EngineTest
    void builtInTypesPrintAndParse(Harness h) {
        h.world.level = 7;
        h.world.message = "42";
        assertEquals("7", h.valueOf("level as_string", STRING));
        assertEquals("3.50", h.valueOf("level / 2 as_string", STRING));
        assertEquals("true", h.valueOf("level > 1 as_string", STRING));
        assertEquals(42, h.valueOf("message as_int", INT));
        assertEquals(42.0, h.valueOf("message as_double", DOUBLE));
        h.world.message = "TRUE";
        assertEquals(true, h.valueOf("message as_boolean", BOOLEAN));
    }

    @EngineTest
    void hostTypesPrintAndParse(Harness h) {
        assertEquals("8", h.valueOf("here y as_string", STRING));
        assertEquals("7 8", h.valueOf("here as_string", STRING));
        assertEquals(new TestTypes.Point(7, 8), h.valueOf("here as_string as_point", TestTypes.POINT));
        assertEquals(true, h.valueOf("here == 7 8", BOOLEAN));
        assertEquals(false, h.valueOf("favourite == blue", BOOLEAN));
        h.world.message = "blue";
        assertEquals(TestTypes.Color.BLUE, h.valueOf("message as_color", TestTypes.COLOR));
    }

    @EngineTest
    void textThatDoesNotParseIsAFailure(Harness h) {
        h.world.message = "seven";
        ScriptFailure failure = h.failure("set_level value_of(message as_int)");
        assertEquals("as_int", failure.faultText());
        h.world.message = "maybe";
        assertEquals("Could not evaluate 'as_boolean': Expected \"true\" or \"false\", got \"maybe\"",
                h.failure("if message as_boolean log x").reason());
    }

    @EngineTest
    void theyWorkInConditions(Harness h) {
        h.world.level = 7;
        h.run("if level > 5 && value_of(level % 2 == 1) log odd");
        h.assertCalls(call("log", "odd"));
    }

    @EngineTest
    void aFailingBuiltInIsPointedAt(Harness h) {
        ScriptFailure failure = h.failure("set_level value_of(level % 0)");
        assertEquals("Could not evaluate '%': / by zero", failure.reason());
        assertEquals("0", failure.faultText());
    }

    @EngineTest
    void aHostMapperReplacesTheBuiltInOne(Harness h) {
        Harness custom = h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.argumentMapper("+", INT, INT, INT, (value, context) -> 0));
        });
        custom.world.level = 7;
        assertEquals(0, custom.valueOf("level + 3", INT));
        assertEquals(10.0, custom.valueOf("level * 1 + 3", DOUBLE));
        assertEquals(1, custom.engine.nodes().stream()
                .filter(node -> node instanceof ScriptArgumentMapper<?, ?, ?> mapper
                        && mapper.displayName().equals("+") && mapper.inputType() == INT)
                .count());
    }
}
