package g_mungus.munguscript.engine_impl.run;

import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.language.node.ScriptContext;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodType;

/**
 * What a node's function is given: the host's context for the run, and the argument written after
 * the node, if it takes one.
 */
public final class NodeContext implements ScriptContext {
    private static final Object NO_ARGUMENT = new Object();

    private final @Nullable Object hostContext;
    private final @Nullable Object argument;

    private NodeContext(@Nullable Object hostContext, @Nullable Object argument) {
        this.hostContext = hostContext;
        this.argument = argument;
    }

    /** A context for a node that takes no argument, from what the host carries on {@code source}. */
    public static <S> NodeContext of(ScriptViewHost<S> host, S source) {
        return new NodeContext(host.hostContext(source), NO_ARGUMENT);
    }

    public NodeContext withArgument(@Nullable Object argument) {
        return new NodeContext(hostContext, argument);
    }

    @Override
    public <H> H host(Class<H> type) {
        if (!type.isInstance(hostContext)) {
            throw new IllegalStateException("The host context is "
                    + (hostContext == null ? "missing" : hostContext.getClass().getSimpleName())
                    + ", not " + type.getSimpleName());
        }
        return type.cast(hostContext);
    }

    @Override
    public <A> A argumentValue(Class<A> type) {
        if (argument == NO_ARGUMENT) {
            throw new IllegalStateException("This node takes no argument");
        }
        // Node functions may ask for int.class; the argument is always boxed.
        @SuppressWarnings("unchecked")
        Class<A> boxed = (Class<A>) MethodType.methodType(type).wrap().returnType();
        return boxed.cast(argument);
    }
}
