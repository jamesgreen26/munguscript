/**
 * Runs the script engine with no Minecraft behind it, for trying scripts out and debugging the
 * engine. {@link g_mungus.munguscript.debug.ScriptDebugMain} runs a {@code .munguscript} file
 * from top to bottom:
 *
 * <pre>
 * #def text = read_file
 * write_file "hello"
 * write_file value_of(read_file + " world")
 * if read_file lines &gt; 0 write_file value_of(text &lt;+ "first\\n")
 * </pre>
 *
 * <p>It is a {@code g_mungus.munguscript.runner.ScriptRunner} with a few nodes of its own and a
 * way of printing problems. Besides the language's built-in types and mappers, scripts have {@code read_file} and
 * {@code write_file}, which read and write {@code output.txt} next to the script.
 *
 * <p>{@link g_mungus.munguscript.debug.DebugLanguageMain} writes the debug language to a language
 * file, so an editor such as the IntelliJ plugin can read debug scripts.
 */
package g_mungus.munguscript.debug;
