/**
 * The MungusScript language: what scripts are made of, and everything needed to extend it. Code
 * that only adds types and nodes needs nothing else.
 *
 * <ul>
 *     <li>this package: {@code ScriptRegistrar}, which collects one build's types and nodes</li>
 *     <li>{@code type}: script types and their keys, and the build environment their argument
 *         types are made with</li>
 *     <li>{@code node}: getters, mappers and executors, the context they run with, what targets
 *         they are meant for, and {@code ScriptNodes} to build them</li>
 *     <li>{@code builtin}: the types and mappers every script has, and the mappers generated for
 *         each writable type</li>
 * </ul>
 *
 * <p>The language depends only on Brigadier and the JDK, never on the engine that builds, parses
 * and runs it.
 */
package g_mungus.munguscript.language;
