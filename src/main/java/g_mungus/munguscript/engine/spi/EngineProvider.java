package g_mungus.munguscript.engine.spi;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * What {@link g_mungus.munguscript.engine.MungusScript} finds through {@link java.util.ServiceLoader}
 * to build engines and views. The library ships one; hosts call {@code MungusScript} and never
 * use this directly.
 */
public interface EngineProvider {

    /** See {@link g_mungus.munguscript.engine.MungusScript#engine}. */
    <S> ScriptEngine<S> engine(ScriptHost<S> host, BuildEnvironment environment,
                               Consumer<ScriptRegistrar> registrations);

    /** See {@link g_mungus.munguscript.engine.MungusScript#view}. */
    <S> ScriptView<S> view(ScriptViewHost<S> host, Collection<ScriptType<?>> types, Collection<Restriction> restrictions,
                           CommandNode<S> graftedUnder);

    /** See {@link g_mungus.munguscript.engine.MungusScript#arguments}. */
    ScriptArguments arguments();
}
