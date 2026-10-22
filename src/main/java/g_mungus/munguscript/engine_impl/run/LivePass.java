package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.language.node.ScriptNode;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One pass of a {@link LiveLine}, carried by the run states of the command and of every
 * {@code value_of} in it.
 *
 * <p>A refresh runs every step, and records what each step that may not run live gave, and which
 * overload each executor would run. A live run gives those steps' recorded values instead of
 * running them, so a chain goes on from the value of its last such step as the refresh worked it
 * out; the steps before it still run, but what they give is replaced. An executor that may not run
 * live fails both.
 *
 * <p>Steps and executors are known by where they stand in the command. Expressions are parsed in
 * place, so that is unique across nested {@code value_of}s, and the same in every pass.
 */
public final class LivePass {
    private final Set<ScriptNode> live;
    private final Recorded recorded;
    private final boolean refreshing;

    private LivePass(Set<ScriptNode> live, Recorded recorded, boolean refreshing) {
        this.live = live;
        this.recorded = recorded;
        this.refreshing = refreshing;
    }

    /** A refresh, recording into a fresh {@link Recorded}. */
    public static LivePass refresh(Set<ScriptNode> live) {
        return new LivePass(live, new Recorded(new HashMap<>(), new HashMap<>()), true);
    }

    /** A live run over what a refresh recorded. */
    public static LivePass live(Set<ScriptNode> live, Recorded recorded) {
        return new LivePass(live, recorded, false);
    }

    /** The pass carried by {@code state}, if it is part of one. */
    static @Nullable LivePass of(@Nullable RunState state) {
        if (state instanceof CommandRun run) {
            return run.pass();
        } else if (state instanceof ValueRun run) {
            return run.pass();
        }
        return null;
    }

    public boolean refreshing() {
        return refreshing;
    }

    /** What this pass recorded, if it is a refresh. */
    public Recorded recorded() {
        return recorded;
    }

    boolean isLive(ScriptNode node) {
        return live.contains(node);
    }

    void record(int position, @Nullable Object value) {
        recorded.values.put(position, value);
    }

    /** What the step at {@code position} gave when the line was refreshed. */
    @Nullable Object recordedValue(int position, ScriptNode node, CommandContext<?> context) {
        if (!recorded.values.containsKey(position)) {
            throw new ScriptFault("'" + node.displayName() + "' has no value yet: the line has not been refreshed"
                    + " since it was reached", context.getInput(), range(context));
        }
        return recorded.values.get(position);
    }

    void recordOverload(int position, int chosen) {
        recorded.overloads.put(position, chosen);
    }

    /** The overload the executor at {@code position} would run when the line was refreshed, or -1 for none. */
    int recordedOverload(int position) {
        return recorded.overloads.getOrDefault(position, -1);
    }

    /** The failure of the executor {@code name}, running in {@code context}, which may not run live. */
    static ScriptFault notLive(String name, CommandContext<?> context) {
        return new ScriptFault("'" + name + "' cannot run live", context.getInput(), range(context));
    }

    /** Where the node running in {@code context} stands: the start of the last node parsed. */
    static int position(CommandContext<?> context) {
        return range(context).getStart();
    }

    private static StringRange range(CommandContext<?> context) {
        List<? extends ParsedCommandNode<?>> nodes = context.getNodes();
        return nodes.get(nodes.size() - 1).getRange();
    }

    /**
     * What a refresh worked out: values by step position, and overloads by executor position. Only
     * read once the refresh that made it has finished.
     */
    public record Recorded(Map<Integer, @Nullable Object> values, Map<Integer, Integer> overloads) {
    }
}
