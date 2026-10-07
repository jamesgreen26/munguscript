package g_mungus.munguscript.runner;

/**
 * A script line that ran.
 *
 * @param line            the line's index in the script, from 0
 * @param command         the command as written, without surrounding whitespace
 * @param executedCommand the command as it ran, after pre-processing
 * @param result          what the command returned
 */
public record CommandResult(int line, String command, String executedCommand, int result) {
}
