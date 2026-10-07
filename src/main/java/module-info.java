/**
 * MungusScript. The language and the engine's API are exported; the engine's implementation,
 * {@code g_mungus.munguscript.engine_impl}, is not, and is reached only as the
 * {@link g_mungus.munguscript.engine.spi.EngineProvider} that {@code MungusScript} loads. The
 * runner, {@code g_mungus.munguscript.runner}, is exported: it runs whole scripts on top of the
 * engine's API, for hosts that need no plumbing of their own. The
 * debug host, {@code g_mungus.munguscript.debug}, is not exported either: it is for running a
 * script from a file with {@code ./gradlew run}, not for hosts to build on.
 */
// Brigadier has no module descriptor; its automatic module name comes from the jar's file name.
@SuppressWarnings("requires-transitive-automatic")
module g_mungus.munguscript {
    requires transitive brigadier;
    requires static org.jetbrains.annotations;

    exports g_mungus.munguscript.language;
    exports g_mungus.munguscript.language.builtin;
    exports g_mungus.munguscript.language.node;
    exports g_mungus.munguscript.language.type;

    exports g_mungus.munguscript.engine;
    exports g_mungus.munguscript.engine.argument;
    exports g_mungus.munguscript.engine.codec;
    exports g_mungus.munguscript.engine.failure;
    exports g_mungus.munguscript.engine.host;
    exports g_mungus.munguscript.engine.preprocess;
    exports g_mungus.munguscript.engine.spi;

    exports g_mungus.munguscript.runner;


    uses g_mungus.munguscript.engine.spi.EngineProvider;
    provides g_mungus.munguscript.engine.spi.EngineProvider
            with g_mungus.munguscript.engine_impl.DefaultEngineProvider;
}
