package g_mungus.munguscript.engine_impl.run;

import g_mungus.munguscript.engine.host.RunState;
import org.jetbrains.annotations.Nullable;

/**
 * The state of one {@code value_of(...)} while it runs: the value its chain has so far. Each
 * evaluation gets a fresh one, so an expression never disturbs the command or condition it is in.
 * Brigadier commands can only return an int, so the last step leaves the result here.
 */
public final class ValueRun implements RunState {
    private final @Nullable LivePass pass;
    private @Nullable Object value;

    /** @param pass the pass of the command it is part of, if any */
    ValueRun(@Nullable LivePass pass) {
        this.pass = pass;
    }

    @Nullable LivePass pass() {
        return pass;
    }

    public @Nullable Object value() {
        return value;
    }

    void set(@Nullable Object value) {
        this.value = value;
    }
}
