/**
 * The MungusScript engine's API: what a host implements and calls to build, parse and run the
 * language in {@code g_mungus.munguscript.language}. Start from {@code MungusScript}, which builds
 * a {@code ScriptEngine} from a {@code ScriptHost} and what the host registers, or a
 * {@code ScriptView} over a tree built elsewhere.
 *
 * <ul>
 *     <li>{@code host}: the run state the host carries, and how its targets match nodes</li>
 *     <li>{@code failure}: failures as data</li>
 *     <li>{@code preprocess}: rewriting command text before parsing, such as aliases and addresses,
 *         and the {@code Rewriter} and {@code SourceMap} to do it with</li>
 *     <li>{@code spi}: how {@code MungusScript} finds the engine implementation</li>
 * </ul>
 *
 * <p>Nothing here depends on Minecraft or a mod loader: MungusScript uses Brigadier and the JDK
 * only, and a Minecraft mod is just one possible host.
 *
 * <p>The engine itself is in {@code g_mungus.munguscript.engine_impl}, which hosts never import.
 * Running whole scripts in one place, with a host that needs no command source of its own, is in
 * {@code g_mungus.munguscript.runner}, and running a script from a file is in
 * {@code g_mungus.munguscript.debug}.
 */
package g_mungus.munguscript.engine;
