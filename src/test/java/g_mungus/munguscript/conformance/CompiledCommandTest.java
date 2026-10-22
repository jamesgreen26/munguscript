package g_mungus.munguscript.conformance;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.CompiledCommand;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.language.node.ScriptNodes;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static g_mungus.munguscript.conformance.World.call;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commands compiled once and run as they stand, and the values of a run shared by every {@code value_of} in it that
 * reads the same.
 */
class CompiledCommandTest {

    /** A harness whose {@code level_read} reads the level, counting each read in {@code reads}. */
    private static Harness counting(Harness h, AtomicInteger reads) {
        return h.with((registrar, world) -> {
            TestNodes.register(registrar, world);
            registrar.register(ScriptNodes.getter("level_read", INT, context -> {
                reads.incrementAndGet();
                return world.level;
            }));
        });
    }

    @EngineTest
    void aCompiledCommandWorksItOutAfreshEachRun(Harness h) throws CommandSyntaxException {
        CompiledCommand<TestHost.Source> command = h.engine.compile("log value_of(level + value_of(counter value))",
                h.engine.begin(h.source()));
        h.world.level = 1;
        h.world.counter.value = 3;
        command.run(h.source());
        h.world.counter.value = 5;
        command.run(h.source());
        h.assertCalls(call("log", "4"), call("log", "6"));
    }

    @EngineTest
    void aValueReadTwiceInARunIsWorkedOutOnce(Harness plain) throws CommandSyntaxException {
        AtomicInteger reads = new AtomicInteger();
        Harness h = counting(plain, reads);
        h.world.level = 2;
        CompiledCommand<TestHost.Source> command = h.engine.compile(
                "log value_of(level + value_of(level_read) + value_of(level_read))", h.engine.begin(h.source()));
        command.run(h.source());
        assertEquals(1, reads.get(), "level_read should be read once in the run");
        h.world.level = 3;
        command.run(h.source());
        assertEquals(2, reads.get(), "level_read should be read again in the next run");
        h.assertCalls(call("log", "6"), call("log", "9"));
    }

    @EngineTest
    void aValueReadTwiceInAPlainRunIsWorkedOutOnce(Harness plain) {
        AtomicInteger reads = new AtomicInteger();
        Harness h = counting(plain, reads);
        h.world.level = 2;
        h.run("log value_of(level + value_of(level_read) + value_of(level_read))");
        assertEquals(1, reads.get(), "level_read should be read once in the run");
        h.assertCalls(call("log", "6"));
    }

    /**
     * A refresh reads every branch in full, so a live run that takes either finds what it needs, even where the same
     * expression stands in both.
     */
    @EngineTest
    void aLiveLineReadsTheSameValueInEitherBranch(Harness plain) throws CommandSyntaxException {
        AtomicInteger reads = new AtomicInteger();
        Harness h = counting(plain, reads);
        h.world.level = 4;
        LiveLine<TestHost.Source> line = h.engine.compileLive(
                "if here x == 7 log value_of(level_read) else log value_of(level_read)", h.source(),
                node -> Set.of("here", "x", "log").contains(node.displayName()));
        line.refresh(h.source());
        line.run(h.source());
        h.assertCalls(call("log", "4"));
    }
}
