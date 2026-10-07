package g_mungus.munguscript.engine_impl.run;

import g_mungus.munguscript.engine.host.RunState;
import org.jetbrains.annotations.Nullable;

/**
 * The state of one {@code value_of(...)} while it runs: the value its chain has so far. Each
 * evaluation gets a fresh one, so an expression never disturbs the command or condition it is in.
 * Brigadier commands can only return an int, so the last step leaves the result here.
 */
public final class ValueRun implements RunState {
    private @Nullable Object value;

    public @Nullable Object value() {
        return value;
    }

    void set(@Nullable Object value) {
        this.value = value;
    }
}
