package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.context.StringRange;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A failure the engine has already explained: a reason worded for the script's author, and where
 * it happened in the text it was raised against. Its message is the reason, so a host that only
 * prints exceptions still shows something readable.
 *
 * <p>Positions are always in the outermost text: expressions are parsed in place, so a fault deep
 * inside nested {@code value_of}s already points into the command. Only the text it knows about is
 * replaced on the way out ({@link #within}), since an inner run sees a prefix of the command.
 *
 * <p>When a node's own function failed, that exception is kept as the cause, so a host can log
 * where in its code the failure came from.
 */
public final class ScriptFault extends RuntimeException {
    private final String input;
    private final @Nullable StringRange range;

    public ScriptFault(String reason, String input, @Nullable StringRange range) {
        this(reason, input, range, null);
    }

    /** A fault explaining {@code cause}, which keeps its own stack trace for the host. */
    public ScriptFault(String reason, String input, @Nullable StringRange range, @Nullable Throwable cause) {
        // The fault itself is data for the script's author; its own stack trace would only cost time.
        super(reason, cause, false, false);
        this.input = input;
        this.range = range;
    }

    public String reason() {
        return getMessage();
    }

    /** The same fault, known to have happened in {@code enclosingInput}, which this one's text begins. */
    public ScriptFault within(String enclosingInput) {
        return new ScriptFault(reason(), enclosingInput, range, getCause());
    }

    /**
     * Where the fault is in {@code command}, given that the text it was raised against ends with
     * {@code command}. A host runs commands behind a prefix of its own ({@code script ...}), which
     * the engine never sees, so the prefix is worked out from the end.
     */
    public Optional<StringRange> rangeIn(String command) {
        if (range == null || !input.endsWith(command)) {
            return Optional.empty();
        }
        int offset = input.length() - command.length();
        if (range.getStart() < offset) {
            return Optional.empty();
        }
        return Optional.of(StringRange.between(range.getStart() - offset, range.getEnd() - offset));
    }
}
