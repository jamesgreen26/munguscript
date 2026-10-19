package g_mungus.munguscript.language.node;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

/**
 * The argument of a {@link ScriptNodes#commandExecutor command executor}: a whole script command,
 * written in brackets as {@code command(...)}. The engine never parses it with this; it gives the
 * executor a slot of its own that reads, checks, highlights and suggests what is in the brackets as a
 * command, and hands the executor its text.
 */
public final class CommandArgument implements ArgumentType<String> {
    public static final CommandArgument INSTANCE = new CommandArgument();

    private CommandArgument() {
    }

    /** The rest of the input, for anything that reads it as a plain argument. */
    @Override
    public String parse(StringReader reader) {
        String rest = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());
        return rest;
    }

    @Override
    public String toString() {
        return "command";
    }
}
