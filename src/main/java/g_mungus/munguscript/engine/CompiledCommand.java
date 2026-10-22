package g_mungus.munguscript.engine;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * One command, parsed once, that the host runs over and over as it stands: a line a block is sent again and again,
 * say. Each run works it out afresh from that parse, with the {@code value_of}s in it read once too.
 *
 * <p>A parse holds what the source it was parsed with allowed, so run it only with sources the host would have
 * parsed it the same way for.
 *
 * @param <S> the host's command source
 */
public interface CompiledCommand<S> {

    /** The command, as it was compiled. */
    String command();

    /**
     * Runs the command.
     *
     * @return what the branch that ran returned, as a command does
     * @throws CommandSyntaxException if Brigadier refuses the command, and whatever a node throws
     */
    int run(S source) throws CommandSyntaxException;
}
