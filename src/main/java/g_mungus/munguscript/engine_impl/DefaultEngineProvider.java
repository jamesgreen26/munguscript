package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.spi.EngineProvider;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Collection;
import java.util.function.Consumer;

/** The engine this library ships, as {@link g_mungus.munguscript.engine.MungusScript} finds it. */
public final class DefaultEngineProvider implements EngineProvider {

    public DefaultEngineProvider() {
    }

    @Override
    public <S> ScriptEngine<S> engine(ScriptHost<S> host, BuildEnvironment environment,
                                      Consumer<ScriptRegistrar> registrations) {
        return new ScriptEngineImpl<>(host, environment, registrations);
    }

    @Override
    public <S> ScriptView<S> view(ScriptViewHost<S> host, Collection<ScriptType<?>> types,
                                  Collection<Restriction> restrictions, CommandNode<S> graftedUnder) {
        return ViewImpl.over(host, types, restrictions, graftedUnder);
    }

    @Override
    public ScriptArguments arguments() {
        return new ScriptArgumentsImpl();
    }
}
