package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.context.StringRange;

/**
 * A {@code %s} standing in an argument slot. It parses, so that a template command can be checked
 * and suggested, but it has to be replaced before the command runs.
 */
public record Placeholder(StringRange range) {
    public static final String TEXT = "%s";
}
