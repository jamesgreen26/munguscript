package g_mungus.munguscript.conformance;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.io.IOException;
import java.util.List;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command executors: {@code later command(...)} is given the command in the brackets as text, and that
 * command reads, highlights and suggests as a command of its own without running. Only the executors meant
 * for {@code later} may be written in it.
 */
class CommandExecutorTest {
    private static final String TRUE = ConditionTest.TRUE;
    private static final String FALSE = ConditionTest.FALSE;

    /** The test nodes, {@code later}, which records the command it is given, and {@code note}, meant for it. */
    private static Harness withLater(Harness h) {
        return h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.commandExecutor("later", (command, context) -> world.record("later", command)));
            registrar.register(ScriptNodes.executor("note", BuiltInTypes.STRING,
                    (text, context) -> world.record("note", text)).onlyInside("later"));
        });
    }

    private static List<String> suggested(ScriptView<TestHost.Source> view, Harness h, String typed) {
        return view.suggest(typed, typed.length(), h.source(), null).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @EngineTest
    void isGivenTheCommandWithoutRunningIt(Harness h) {
        Harness l = withLater(h);
        l.run("later command(note red)");
        l.assertCalls(call("later", "note red"));
    }

    @EngineTest
    void runsOnlyInTheBranchThatRuns(Harness h) {
        Harness l = withLater(h);
        l.run("if " + FALSE + " later command(note a)");
        l.assertCalls();
        l.run("if " + TRUE + " later command(if " + FALSE + " note a else note b)");
        l.assertCalls(call("later", "if " + FALSE + " note a else note b"));
        l.world.calls.clear();
        // The brackets end the command, so an else after them is the line's own.
        l.run("if " + FALSE + " later command(note a) else log b");
        l.assertCalls(call("log", "b"));
    }

    @EngineTest
    void theCommandMustBeWrappedAndOneThatRuns(Harness h) {
        Harness l = withLater(h);
        l.failure("later");
        l.failure("later note red");
        l.failure("later command(frob)");
        l.failure("later command(note)");
        l.failure("later command(note a");
        l.assertCalls();
        // Getters and mappers are not limited.
        l.run("later command(note value_of(level))");
        l.assertCalls(call("later", "note value_of(level)"));
    }

    @EngineTest
    void onlyTheExecutorsMeantForItParseInIt(Harness h) throws IOException {
        Harness l = withLater(h);
        assertTrue(l.failure("later command(log a)").reason().contains("'log' cannot be used inside later"),
                "log should be refused inside later");
        l.failure("later command(if " + TRUE + " note a else log b)");
        l.failure("later command(later command(note a))");
        // Named even in a command that would not run anyway.
        assertTrue(l.failure("later command(log)").reason().contains("'log' cannot be used inside later"),
                "log without its argument should be refused for being inside later");
        l.assertCalls();
        ScriptView<TestHost.Source> view = CodecTest.roundTrip(l);
        assertFalse(view.parse("later command(log a)", l.source()).getExceptions().isEmpty(),
                "a decoded tree should refuse log inside later too");
        assertTrue(view.parse("later command(note a)", l.source()).getExceptions().isEmpty(),
                "and take note");
    }

    @EngineTest
    void theCommandIsSuggestedAndHighlightedAsOne(Harness h) {
        Harness l = withLater(h);
        assertTrue(l.suggest("later ", null).contains("command("), "command( should be suggested after later");
        assertTrue(l.suggest("later com", null).contains("command("), "command( should complete");
        assertTrue(l.suggest("later command(no", null).contains("note"), "note should be suggested inside");
        assertTrue(l.suggest("later command(", null).contains("if"), "a condition should be suggested inside");
        assertTrue(l.suggest("later command(note red", null).contains(")"), ") should be offered once it could end");
        assertEquals(List.of("EXECUTOR:later", "ARGUMENT:command(", "EXECUTOR:note", "ARGUMENT:red", "ARGUMENT:)"),
                HighlightTest.highlights(l.engine, l, "later command(note red)", null));
        // Still open: the brackets and what is in them are highlighted as far as they go.
        assertEquals(List.of("EXECUTOR:later", "ARGUMENT:command(", "EXECUTOR:note"),
                HighlightTest.highlights(l.engine, l, "later command(note", null));
    }

    /** Inside an open command(, the parse to hint from is the command in it, in the line's positions. */
    @EngineTest
    void parseAtGivesTheCommandTheCursorIsIn(Harness h) {
        Harness l = withLater(h);
        String typed = "later command(note ";
        var inner = l.engine.parseAt(typed, typed.length(), l.source());
        assertEquals("note", inner.getContext().getNodes().get(0).getNode().getName());
        assertEquals(14, inner.getContext().getNodes().get(0).getRange().getStart());
        String closed = "later command(note red) else log x";
        assertEquals("later", l.engine.parseAt(closed, closed.length(), l.source()).getContext().getNodes().get(0)
                .getNode().getName());
    }

    @EngineTest
    void aDecodedTreeKnowsItToo(Harness h) throws IOException {
        Harness l = withLater(h);
        ScriptView<TestHost.Source> view = CodecTest.roundTrip(l);
        List<String> suggested = suggested(view, l, "later command(no");
        assertTrue(suggested.contains("note"), "a decoded tree should suggest inside command(, suggests " + suggested);
        assertEquals(List.of("EXECUTOR:later", "ARGUMENT:command(", "EXECUTOR:note", "ARGUMENT:red", "ARGUMENT:)"),
                HighlightTest.highlights(view, l, "later command(note red)", null));
    }

    @EngineTest
    void onlyTheExecutorsMeantForItAreSuggestedInIt(Harness h) throws IOException {
        Harness l = withLater(h);
        assertFalse(l.suggest("no", null).contains("note"), "note should not be offered at the top of a command");
        assertFalse(l.suggest("if " + TRUE + " no", null).contains("note"), "nor after a condition outside later");
        assertTrue(l.suggest("if " + TRUE + " lo", null).contains("log"), "where log is");
        assertTrue(l.suggest("later command(if " + TRUE + " no", null).contains("note"),
                "note should be offered after a condition inside later");
        assertFalse(l.suggest("later command(lo", null).contains("log"), "log should not be offered inside later");
        assertFalse(l.suggest("later command(lat", null).contains("later"), "nor later inside itself");
        ScriptView<TestHost.Source> view = CodecTest.roundTrip(l);
        assertTrue(suggested(view, l, "later command(no").contains("note"), "a decoded tree should know it too");
        assertFalse(suggested(view, l, "later command(lo").contains("log"), "and leave out log inside later");
        assertFalse(suggested(view, l, "no").contains("note"), "and note at the top");
        // At the top of a command it only steers suggestions: written there, it still runs.
        l.run("note x");
        l.assertCalls(call("note", "x"));
    }

    @EngineTest
    void anExecutorMayOnlyBeForInsideACommandExecutor(Harness h) {
        boolean refused = false;
        try {
            h.with((registrar, world) -> {
                TestNodes.register(registrar, world);
                registrar.register(ScriptNodes.executor("step", BuiltInTypes.INT, (value, context) -> 0)
                        .onlyInside("log"));
            });
        } catch (IllegalStateException e) {
            refused = true;
        }
        assertTrue(refused, "step only inside log, which takes no command(...), should be refused");
    }

    @EngineTest
    void itsVocabularySaysItTakesACommand(Harness h) {
        Vocabulary.Word later = withLater(h).engine.vocabulary().executors().stream()
                .filter(word -> word.name().equals("later")).findFirst().orElseThrow();
        assertEquals("command", later.forms().get(0).argument());
    }

    @EngineTest
    void aLiveLineCannotHoldOneThatMayNotRunLive(Harness h) throws CommandSyntaxException {
        Harness l = withLater(h);
        LiveLine<TestHost.Source> line = l.engine.compileLive("later command(note x)", l.source(), node -> false);
        RuntimeException fault = assertThrows(RuntimeException.class, () -> line.refresh(l.source()));
        assertTrue(fault.getMessage().contains("'later' cannot run live"), "the fault should name later: " + fault.getMessage());
        l.assertCalls();
    }

    @EngineTest
    void noOtherExecutorMayShareItsName(Harness h) {
        boolean refused = false;
        try {
            h.with((registrar, world) -> {
                TestNodes.register(registrar, world);
                registrar.register(ScriptNodes.commandExecutor("log", (command, context) -> 0));
            });
        } catch (IllegalStateException e) {
            refused = true;
        }
        assertTrue(refused, "a command executor named like log should be refused");
    }
}
