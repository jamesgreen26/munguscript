package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.conformance.TestTypes.Point;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expressions, evaluated through {@code ScriptEngine.evaluate} as {@code value_of(...)} would be. */
class ValueOfTest {

    @EngineTest
    void aGetterOnItsOwn(Harness h) {
        assertEquals(new Point(7, 8), h.valueOf("here", TestTypes.POINT));
        assertEquals(Color.RED, h.valueOf("favourite", TestTypes.COLOR));
        assertSame(h.world.counter, h.valueOf("counter", TestTypes.COUNTER));
    }

    @EngineTest
    void mappersChainFromAGetter(Harness h) {
        h.world.favourite = Color.GREEN;
        h.world.counter.value = 5;
        assertEquals(7, h.valueOf("here x", INT));
        assertEquals("green", h.valueOf("favourite name", STRING));
        assertEquals(true, h.valueOf("counter value is_positive", BOOLEAN));
        assertEquals(false, h.valueOf("counter value is_positive not", BOOLEAN));
    }

    @EngineTest
    void aChainCanPassThroughTypesOtherThanTheOneWanted(Harness h) {
        h.world.level = 30;
        assertEquals(true, h.valueOf("level to_celsius warm", BOOLEAN));
        assertEquals("30.0", h.valueOf("level to_celsius as_string", STRING));
    }

    @EngineTest
    void mapperArgumentsCanBeRelativeOrValueOf(Harness h) {
        h.world.level = 4;
        assertEquals(new Point(8, 10), h.valueOf("here plus 1 2", TestTypes.POINT));
        assertEquals(new Point(7 + 101, 8 + 200), h.valueOf("here plus ~1 ~", TestTypes.POINT));
        assertEquals(new Point(12, 13), h.valueOf("here plus value_of(origin plus 5 5)", TestTypes.POINT));
        assertEquals(16, h.valueOf("level scale value_of(level)", INT));
        assertEquals(new Point(14, 16),
                h.valueOf("here plus value_of(origin plus value_of(here plus value_of(origin)))", TestTypes.POINT));
    }

    @EngineTest
    void aRawArgumentIsPassedAsItParsedAndTakesNoValueOf(Harness h) {
        h.world.message = "hello";
        assertEquals(true, h.valueOf("message matches \"h.*\"", BOOLEAN));
        assertEquals(false, h.valueOf("message matches x", BOOLEAN));
        assertThrows(RuntimeException.class, () -> h.valueOf("message matches value_of(message)", BOOLEAN));
    }

    @EngineTest
    void eachEvaluationReadsTheWorldAfresh(Harness h) {
        h.world.level = 1;
        assertEquals(1, h.valueOf("level", INT));
        h.world.level = 2;
        assertEquals(2, h.valueOf("level", INT));
    }

    @EngineTest
    void aFailingGetterSaysWhy(Harness h) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> h.valueOf("explode", INT));
        assertTrue(e.getMessage().contains("the getter exploded"), e.getMessage());
    }

    @EngineTest
    void anExpressionOfTheWrongTypeIsRefused(Harness h) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> h.valueOf("here", INT));
        assertEquals("value_of(here) gives test:point, but the command needs int", e.getMessage());
    }

    @EngineTest
    void aProbeSaysAnExpressionOfTheWrongTypeDoesNotReadAsTheTypeWanted(Harness h) {
        var probe = h.engine.probe(h.source());
        assertTrue(probe.readsAs("level", INT.key()));
        assertFalse(probe.readsAs("here", INT.key()));
        assertFalse(probe.readsAs("message", INT.key()));
        assertFalse(probe.readsAs("level", BOOLEAN.key()));
        assertFalse(probe.readsAs("favourite", TestTypes.POINT.key()));
        assertTrue(probe.readsAs("here plus value_of(origin)", TestTypes.POINT.key()));
        assertFalse(probe.readsAs("here plus value_of(level)", TestTypes.POINT.key()));
        assertFalse(probe.readsAs("level scale value_of(here)", INT.key()));
    }

    @EngineTest
    void aWrongTypeDeepInsideNestedValueOfsIsRefused(Harness h) {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> h.valueOf("here plus value_of(origin plus value_of(level))", TestTypes.POINT));
        assertTrue(e.getMessage().contains("value_of(level)"), e.getMessage());
    }

    @EngineTest
    void aNestedFailureKeepsTheInnermostReason(Harness h) {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> h.valueOf("here plus value_of(origin plus value_of(frobnicate))", TestTypes.POINT));
        assertEquals("'frobnicate' is not a known value in value_of(frobnicate)", e.getMessage());
    }
}
