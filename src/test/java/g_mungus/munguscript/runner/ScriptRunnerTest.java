package g_mungus.munguscript.runner;

import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptNodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptRunnerTest {

    /** What the nodes read and write: the host context of every run. */
    record Notebook(int level, List<Object> notes) {
        Notebook(int level) {
            this(level, new ArrayList<>());
        }

        int note(Object note) {
            notes.add(note);
            return 1;
        }
    }

    /** Meant for lamps only. */
    record Lamps() implements Applicability {
    }

    private static void register(ScriptRegistrar registrar) {
        registrar.register(ScriptNodes.getter("level", INT, context -> context.host(Notebook.class).level()));
        registrar.register(ScriptNodes.executor("note", INT,
                (value, context) -> context.host(Notebook.class).note(value)));
        registrar.register(ScriptNodes.executor("twice", INT, (value, context) -> {
            context.host(Notebook.class).note(value);
            return 2;
        }));
    }

    private final ScriptRunner<SimpleSource> runner = ScriptRunner.builder("test").register(ScriptRunnerTest::register).build();
    private final Notebook notebook = new Notebook(5);

    private ScriptResult run(String script) {
        return runner.run(script, new SimpleSource(notebook));
    }

    @Test
    void linesRunInOrderAndSayWhatTheyReturned() {
        ScriptResult result = run("""
                #def level_up = level + 5
                note value_of(level)

                  twice value_of(level_up)
                """);
        assertTrue(result.succeeded(), result.toString());
        assertEquals(List.of(
                new CommandResult(1, "note value_of(level)", "note value_of(level)", 1),
                new CommandResult(3, "twice value_of(level_up)", "twice value_of(level + 5)", 2)
        ), result.commands());
        assertEquals(List.of(5, 10), notebook.notes());
    }

    @Test
    void aSingleCommandIsAScriptOfOneLine() {
        assertTrue(run("note 3").succeeded());
        assertEquals(List.of(3), notebook.notes());
    }

    @Test
    void theFirstFailureStopsTheScriptAndIsLocatedInIt() {
        ScriptResult result = run("""
                note 1
                  note value_of(level % 0)
                note 3
                """);
        assertEquals(List.of(1), notebook.notes());
        assertEquals(1, result.commands().size());
        ScriptProblem problem = result.problems().get(0);
        assertEquals(1, result.problems().size());
        assertEquals(1, problem.line());
        assertEquals("note value_of(level % 0)", problem.text());
        assertNotNull(problem.failure());
        assertEquals(problem.failure().faultRange(), problem.range());
        assertEquals(problem.failure().reason(), problem.reason());
    }

    @Test
    void problemsPreparingTheScriptStopItBeforeAnythingRuns() {
        ScriptResult result = run("""
                #def ok = level
                #def + = level
                note 1
                """);
        assertEquals(List.of(), notebook.notes());
        assertEquals(List.of(), result.commands());
        ScriptProblem problem = result.problems().get(0);
        assertEquals(1, problem.line());
        assertEquals("#def + = level", problem.text());
        assertTrue(problem.reason().startsWith("Invalid alias declaration"), problem.reason());
        assertNull(problem.failure());
    }

    @Test
    void theHostsOwnPreProcessorsRunAfterTheAliasesItKeeps() {
        ScriptRunner<SimpleSource> censored = ScriptRunner.builder("test")
                .register(ScriptRunnerTest::register)
                .preProcessors(engine -> List.of(engine.aliases(), new Refuse("level")))
                .build();
        // The refusal sees the alias expanded, and the problem points at the line as written.
        ScriptResult result = censored.run("""
                #def secret = level
                note 1
                note value_of(secret)
                note 2
                """, new SimpleSource(notebook));
        assertEquals(List.of(1), notebook.notes());
        ScriptProblem problem = result.problems().get(0);
        assertEquals(2, problem.line());
        assertEquals("'level' is not allowed", problem.reason());
        assertEquals("note value_of(secret)", problem.text());
        assertNull(problem.failure());
    }

    @Test
    void registrationsAddUpInOneBuild() {
        ScriptRunner<SimpleSource> both = ScriptRunner.builder("test")
                .register(ScriptRunnerTest::register)
                .register(registrar -> registrar.register(ScriptNodes.getter("eleven", INT, context -> 11)))
                .build();
        assertTrue(both.run("note value_of(eleven)", new SimpleSource(notebook)).succeeded());
        assertEquals(List.of(11), notebook.notes());
    }

    @Test
    void aSimpleHostWithoutAMatcherHasNoTargets() {
        ScriptRunner<SimpleSource> overloaded = overloadedRunner(new SimpleHost("test"));
        assertTrue(overloaded.run("glow 4", new SimpleSource(notebook)).succeeded());
        assertEquals(List.of("anything 4"), notebook.notes());
    }

    @Test
    void aSimpleHostsMatcherChoosesBetweenOverloads() {
        ScriptRunner<SimpleSource> overloaded = overloadedRunner(new SimpleHost("test",
                (applicability, context) -> applicability instanceof Lamps ? Match.EXPLICIT : Match.NONE));
        assertTrue(overloaded.run("glow 4", new SimpleSource(notebook)).succeeded());
        assertEquals(List.of("lamp 4"), notebook.notes());
    }

    @Test
    void anExpressionEvaluatesAgainstTheSourcesContext() {
        assertEquals(15, runner.evaluate("level + 10", INT, new SimpleSource(notebook)));
    }

    private static ScriptRunner<SimpleSource> overloadedRunner(SimpleHost host) {
        return ScriptRunner.builder(host).register(registrar -> {
            registrar.register(ScriptNodes.executor("glow", INT,
                            (value, context) -> context.host(Notebook.class).note("lamp " + value))
                    .withApplicability(new Lamps()));
            registrar.register(ScriptNodes.executor("glow", INT,
                    (value, context) -> context.host(Notebook.class).note("anything " + value)));
        }).build();
    }

    /** A host pre-processor that refuses commands containing a word. */
    private record Refuse(String word) implements CommandPreProcessor {
        @Override
        public Prepared prepare(List<String> scriptLines, PreProcessContext context) {
            return new Prepared() {
                @Override
                public Set<Integer> consumedLines() {
                    return Set.of();
                }

                @Override
                public List<PreProcessDiagnostic> diagnostics() {
                    return List.of();
                }

                @Override
                public PreProcessed process(String command, PreProcessContext context) {
                    return command.contains(word)
                            ? new PreProcessed(command, SourceMap.IDENTITY,
                            List.of(PreProcessDiagnostic.inCommand("'" + word + "' is not allowed", null)))
                            : PreProcessed.unchanged(command);
                }

                @Override
                public Collection<PreProcessorToken> tokens() {
                    return List.of();
                }
            };
        }
    }
}
