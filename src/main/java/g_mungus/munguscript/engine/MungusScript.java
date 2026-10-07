package g_mungus.munguscript.engine;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.spi.EngineProvider;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Collection;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/** Where a host starts: building an engine, or a view over a tree built elsewhere. */
public final class MungusScript {

    private MungusScript() {
    }

    /**
     * Builds a tree from whatever {@code registrations} registers, plus the engine's built-in
     * types and mappers and the mappers generated for each writable type.
     */
    public static <S> ScriptEngine<S> engine(ScriptHost<S> host, BuildEnvironment environment,
                                             Consumer<ScriptRegistrar> registrations) {
        return Provider.INSTANCE.engine(host, environment, registrations);
    }

    /**
     * A view over a tree built elsewhere, such as the one a client received from the server.
     * {@code graftedUnder} is the node the server passed to {@link ScriptEngine#graft},
     * {@code types} are the host's own types (the built-in ones are always known), and
     * {@code restrictions} are the server engine's {@link ScriptEngine#restrictions}. A view runs
     * nothing, so {@code host} need not carry run state.
     *
     * <p>A client that rebuilt the engine's argument types makes its view with
     * {@link ScriptArguments.Rebuild#view} instead, which points them at it.
     */
    public static <S> ScriptView<S> view(ScriptViewHost<S> host, Collection<ScriptType<?>> types,
                                         Collection<Restriction> restrictions, CommandNode<S> graftedUnder) {
        return Provider.INSTANCE.view(host, types, restrictions, graftedUnder);
    }

    /**
     * Describing the engine's argument types on a server and rebuilding them on a client, for a
     * host that sends its command tree across a connection.
     */
    public static ScriptArguments arguments() {
        return Provider.INSTANCE.arguments();
    }

    /** Found on first use. */
    private static final class Provider {
        // This library's own class loader, not the thread's: under a mod loader the thread's
        // context loader may not see the library's service file.
        static final EngineProvider INSTANCE = ServiceLoader
                .load(EngineProvider.class, EngineProvider.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + EngineProvider.class.getName()
                        + " found; is the MungusScript jar's META-INF/services intact?"));
    }
}
