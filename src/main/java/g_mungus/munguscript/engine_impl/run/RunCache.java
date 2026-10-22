package g_mungus.munguscript.engine_impl.run;

import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What one run of a command shares with every {@code value_of} in it: the parses of a command that is run over and
 * over, if it is one, and the values its {@code value_of}s have given so far in this run.
 *
 * <p>Getters and mappers only read, so the same expression gives the same value however often it stands in one
 * command, and it is worked out once. Not while a {@link LivePass} refreshes, which records each step by where it
 * stands: a repeat left unread would leave its steps unrecorded for a later run that reaches them first.
 */
final class RunCache {
    private record Key(String expression, TypeKey target) {
    }

    private final @Nullable Parses parses;
    private final Map<Key, @Nullable Object> values = new HashMap<>();

    RunCache(@Nullable Parses parses) {
        this.parses = parses;
    }

    /** The cache {@code state} carries, if it is a run's. */
    static @Nullable RunCache of(@Nullable RunState state) {
        if (state instanceof CommandRun run) {
            return run.cache();
        } else if (state instanceof ValueRun run) {
            return run.cache();
        }
        return null;
    }

    @Nullable Parses parses() {
        return parses;
    }

    boolean has(String expression, TypeKey target) {
        return values.containsKey(new Key(expression, target));
    }

    @Nullable Object get(String expression, TypeKey target) {
        return values.get(new Key(expression, target));
    }

    void put(String expression, TypeKey target, @Nullable Object value) {
        values.put(new Key(expression, target), value);
    }
}
