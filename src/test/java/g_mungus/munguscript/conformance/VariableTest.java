package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.ScriptVariable;
import g_mungus.munguscript.language.builtin.BuiltInTypes;

import java.util.List;
import java.util.Map;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code #var name = literal}: a literal alias whose value the host passes in, as plain text, with each command. */
class VariableTest {

    @EngineTest
    void aVariableStandsAtItsDeclaredLiteralWhenTheHostHasNoValue(Harness h) {
        h.run("""
                #var lvl = 4
                set_level value_of(lvl)
                """);
        h.assertCalls(call("set_level", 4));
    }

    @EngineTest
    void aVariableStandsAtTheValueTheHostPasses(Harness h) {
        h.variables = Map.of("lvl", "7");
        h.run("""
                #var lvl = 4
                set_level value_of(lvl)
                """);
        h.assertCalls(call("set_level", 7));
    }

    @EngineTest
    void aVariableCanBeACondition(Harness h) {
        h.variables = Map.of("ready", "true");
        h.run("""
                #var ready = false
                if ready log yes else log no
                """);
        h.assertCalls(call("log", "yes"));
    }

    @EngineTest
    void aValueIsWhateverTypeItReadsAs(Harness h) {
        h.variables = Map.of("text", "7");
        h.run("""
                #var text = hello
                set_level value_of(text)
                """);
        h.assertCalls(call("set_level", 7));
    }

    @EngineTest
    void aValueThatIsNoNumberIsAString(Harness h) {
        h.variables = Map.of("lvl", "hello world");
        h.run("""
                #var lvl = 4
                log value_of(lvl)
                """);
        h.assertCalls(call("log", "hello world"));
    }

    @EngineTest
    void anAliasUsingAVariableTakesItsValueWhenTheCommandIsPreProcessed(Harness h) {
        h.variables = Map.of("lvl", "3");
        h.run("""
                #var lvl = 1
                #def doubled = lvl scale 2
                set_level value_of(doubled)
                """);
        h.assertCalls(call("set_level", 6));
    }

    @EngineTest
    void aVariableMustBeALiteral(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #var lvl = level
                set_level 1
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(0,
                "Variable must be a literal: a number, true or false, or a string")), diagnostics);
    }

    @EngineTest
    void aVariableCannotBeDeclaredTwice(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #var lvl = 1
                #var lvl = 2
                set_level 1
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(1, "Variable 'lvl' is already declared")), diagnostics);
    }

    @EngineTest
    void aVariableBelowAnAliasOfTheSameNameIsRefused(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #def lvl = level
                #var lvl = 2
                set_level 1
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(1, "Variable name 'lvl' conflicts with an alias")),
                diagnostics);
    }

    @EngineTest
    void anAliasBelowAVariableOfTheSameNameIsRefused(Harness h) {
        List<PreProcessDiagnostic> diagnostics = h.diagnostics("""
                #var lvl = 2
                #def lvl = level
                set_level 1
                """);
        assertEquals(List.of(PreProcessDiagnostic.atLine(1, "Alias name 'lvl' conflicts with a variable")),
                diagnostics);
    }

    @EngineTest
    void aVariableIsListedOnlyBelowItsDeclaration(Harness h) {
        CommandPreProcessor.Prepared prepared = h.prepare("""
                set_level 1
                #var lvl = 2
                set_level 1
                """);
        assertEquals(Map.of(), prepared.at(0).variables());
        assertEquals(Map.of("lvl", new ScriptVariable("lvl", BuiltInTypes.INT.key(), "2")),
                prepared.at(2).variables());
    }

    @EngineTest
    void aValueThatDoesNotSuitWhereItIsUsedFailsThere(Harness h) {
        h.variables = Map.of("lvl", "hello");
        h.failure("""
                #var lvl = 4
                set_level value_of(lvl)
                """);
    }

    @EngineTest
    void aStringValueCannotReachPastItsLiteral(Harness h) {
        h.variables = Map.of("t", "a\") log b (\"c\" \\");
        h.run("""
                #var t = x
                log value_of(t)
                """);
        h.assertCalls(call("log", "a\") log b (\"c\" \\"));
    }
}
