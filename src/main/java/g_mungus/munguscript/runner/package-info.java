/**
 * Running whole scripts without writing a host's plumbing: {@code ScriptRunner} builds the engine,
 * hosts it in a dispatcher of its own, and runs a script top to bottom, returning what ran and
 * what stopped it as data. {@code SimpleHost} and {@code SimpleSource} are a host and source for
 * a program that has no command source of its own.
 *
 * <p>Built only on {@code g_mungus.munguscript.engine} and {@code g_mungus.munguscript.language}.
 * A host whose scripts are sequenced in one place and run in another, as described in
 * {@code SCRIPT_LANGUAGE_CONSTRAINTS.md}, uses the engine directly instead.
 */
package g_mungus.munguscript.runner;
