package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/**
 * A {@code value_of(...)} that cannot stand where it was written, found while parsing. It is a
 * syntax error like any other to Brigadier, but it keeps the whole {@code value_of(...)} it is
 * about, so a failure can point at all of it rather than at one word. When it is about a nested
 * {@code value_of}, it is passed outward unchanged, keeping the innermost reason.
 */
public final class ValueOfException extends CommandSyntaxException {
    private static final SimpleCommandExceptionType TYPE =
            new SimpleCommandExceptionType(new LiteralMessage("value_of(...) cannot be used here"));

    private final StringRange range;

    public ValueOfException(String reason, String input, StringRange range) {
        super(TYPE, new LiteralMessage(reason), input, range.getStart());
        this.range = range;
    }

    public String reason() {
        return getRawMessage().getString();
    }

    /** The {@code value_of(...)} at fault, in the text it was parsed from. */
    public StringRange range() {
        return range;
    }
}
