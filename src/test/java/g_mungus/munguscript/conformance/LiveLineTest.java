package g_mungus.munguscript.conformance;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.LivePolicy;

import java.util.Set;

import static g_mungus.munguscript.conformance.World.call;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lines compiled once and run live: words that may not run live are worked out by a refresh, and
 * executors that may not are refused.
 */
class LiveLineTest {
    /** {@code level} and {@code set_level} may not run live; the rest of the words used here may. */
    private static final LivePolicy POLICY = node -> Set.of("here", "x", "counter", "value", "is_positive", "log",
            "scale").contains(node.displayName());

    private static LiveLine<TestHost.Source> compile(Harness h, String command) throws CommandSyntaxException {
        LiveLine<TestHost.Source> line = h.engine.compileLive(command, h.source(), POLICY);
        line.refresh(h.source());
        return line;
    }

    private static void run(Harness h, LiveLine<TestHost.Source> line) throws CommandSyntaxException {
        line.run(h.source());
    }

    @EngineTest
    void aWordThatMayNotRunLiveReadsAsAtTheLastRefresh(Harness h) throws CommandSyntaxException {
        h.world.level = 1;
        LiveLine<TestHost.Source> line = compile(h, "if level is_positive log yes else log no");
        h.world.level = 0;
        run(h, line);
        h.assertCalls(call("log", "yes"));
        line.refresh(h.source());
        run(h, line);
        h.assertCalls(call("log", "yes"), call("log", "no"));
    }

    @EngineTest
    void wordsAfterThePreResolvedPartRunLive(Harness h) throws CommandSyntaxException {
        h.world.level = 2;
        LiveLine<TestHost.Source> line = compile(h, "log value_of(level scale value_of(counter value))");
        h.world.counter.value = 3;
        h.world.level = 100;
        run(h, line);
        // level is from the refresh, the counter from now.
        h.assertCalls(call("log", "6"));
    }

    @EngineTest
    void aRefreshRunsNoExecutorsButReadsEveryBranch(Harness h) throws CommandSyntaxException {
        h.world.level = 4;
        LiveLine<TestHost.Source> line = compile(h,
                "if here x == 7 log value_of(counter value) else log value_of(level)");
        h.assertCalls();
        h.world.level = 9;
        run(h, line);
        h.assertCalls(call("log", "0"));
    }

    @EngineTest
    void anExecutorThatMayNotRunLiveFailsTheRefresh(Harness h) throws CommandSyntaxException {
        LiveLine<TestHost.Source> line = h.engine.compileLive("set_level value_of(counter value)", h.source(), POLICY);
        RuntimeException fault = assertThrows(RuntimeException.class, () -> line.refresh(h.source()));
        assertTrue(fault.getMessage().contains("'set_level' cannot run live"), "the fault should name set_level: "
                + fault.getMessage());
        // In whichever branch it stands, since a refresh reads them all.
        LiveLine<TestHost.Source> branch = h.engine.compileLive("if here x == 7 log a else set_level 1", h.source(),
                POLICY);
        assertThrows(RuntimeException.class, () -> branch.refresh(h.source()));
        assertThrows(IllegalStateException.class, () -> line.run(h.source()));
        h.assertCalls();
    }

    @EngineTest
    void aLineMustBeRefreshedBeforeItRuns(Harness h) throws CommandSyntaxException {
        LiveLine<TestHost.Source> line = h.engine.compileLive("log x", h.source(), POLICY);
        assertThrows(IllegalStateException.class, () -> line.run(h.source()));
    }

    @EngineTest
    void aLineThatDoesNotParseIsRefused(Harness h) {
        assertThrows(CommandSyntaxException.class, () -> h.engine.compileLive("log", h.source(), POLICY));
        assertThrows(CommandSyntaxException.class, () -> h.engine.compileLive("nonsense 1", h.source(), POLICY));
    }
}
