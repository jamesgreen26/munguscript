package g_mungus.munguscript.engine;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * One command, parsed once, that the host runs over and over where only some words may run: on a
 * physics thread, say, that must not touch the world. Which words may is the {@link LivePolicy} it
 * was compiled with.
 *
 * <p>A chain that has a word that may not run live is worked out ahead of time by {@link #refresh},
 * up to and including the last such word, and {@link #run} starts from that value. Everything in that
 * part of the chain, a {@code value_of} in a mapper's argument included, reads as it stood at the
 * refresh. An executor must be able to run live: one that may not fails the refresh that reaches it.
 *
 * <p>{@link #refresh} and {@link #run} may be called on different threads at once: a run reads the
 * values of the last refresh that finished.
 *
 * @param <S> the host's command source
 */
public interface LiveLine<S> {

    /** The command, as it was compiled. */
    String command();

    /**
     * Works out the parts of the command that may not run live, for the runs that follow. Every
     * condition and argument is read, whichever branch would run; no executor runs.
     *
     * @throws CommandSyntaxException if Brigadier refuses the command, and whatever a node throws, as
     *                                for an executor that may not run live
     */
    void refresh(S source) throws CommandSyntaxException;

    /**
     * Runs the command live. Steps worked out by the last {@link #refresh} are not run again.
     *
     * @return what the branch that ran returned, as a command does
     * @throws IllegalStateException  if the line was never refreshed
     * @throws CommandSyntaxException if Brigadier refuses the command, and whatever a node throws
     */
    int run(S source) throws CommandSyntaxException;
}
