package g_mungus.munguscript.engine_impl.preprocess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionStartsTest {

    @Test
    void aConditionStartsAnExpression() {
        assertEquals(List.of(3), ExpressionStarts.inCommand("if a log b"));
        assertEquals(List.of(7), ExpressionStarts.inCommand("unless a log b"));
    }

    @Test
    void soDoesAConditionAfterElse() {
        assertEquals(List.of(3, 19), ExpressionStarts.inCommand("if a log b else if c log d"));
    }

    @Test
    void ifInTheMiddleOfACommandDoesNot() {
        assertEquals(List.of(), ExpressionStarts.inCommand("log if x"));
    }

    @Test
    void valueOfStartsOneAtAnyDepth() {
        assertEquals(List.of(13, 29), ExpressionStarts.inCommand("set value_of(a plus value_of(b))"));
        assertEquals(List.of(14), ExpressionStarts.inCommand("set value_of( a"));
    }

    @Test
    void quotedTextIsNeverAnExpression() {
        assertEquals(List.of(3), ExpressionStarts.inCommand("if a log \"value_of(x) else if y\""));
    }

    @Test
    void wordsInsideValueOfAreNotTheCommandsKeywords() {
        assertEquals(List.of(3, 18), ExpressionStarts.inCommand("if a log value_of(m else if b)"));
    }

    @Test
    void anExpressionStartsWithOne() {
        assertEquals(List.of(1, 17), ExpressionStarts.inExpression(" a plus value_of(b)"));
    }
}
