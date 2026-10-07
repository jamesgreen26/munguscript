package g_mungus.munguscript.runner;

import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * A host for a program with no command source of its own: its source is a {@link SimpleSource},
 * which carries the host's context and the engine's run state.
 *
 * <p>Unless it is given a matcher, it has no targets, so a node restricted to some is never meant
 * for the run: it is not suggested, and of an executor's overloads it only runs as the
 * unrestricted one's fallback.
 */
public final class SimpleHost implements ScriptHost<SimpleSource> {
    private final String defaultNamespace;
    private final BiFunction<Applicability, ScriptContext, Match> matcher;

    /**
     * A host with no targets.
     *
     * @param defaultNamespace the namespace whose types scripts write by path alone
     */
    public SimpleHost(String defaultNamespace) {
        this(defaultNamespace, (applicability, context) -> Match.NONE);
    }

    /**
     * A host whose targets {@code matcher} answers for, as {@link ScriptHost#match} would. It is
     * only asked about nodes with an applicability.
     *
     * @param defaultNamespace the namespace whose types scripts write by path alone
     */
    public SimpleHost(String defaultNamespace, BiFunction<Applicability, ScriptContext, Match> matcher) {
        this.defaultNamespace = defaultNamespace;
        this.matcher = matcher;
    }

    @Override
    public @Nullable RunState runState(SimpleSource source) {
        return source.runState();
    }

    @Override
    public SimpleSource withRunState(SimpleSource source, RunState state) {
        return new SimpleSource(source.context(), state);
    }

    @Override
    public @Nullable Object hostContext(SimpleSource source) {
        return source.context();
    }

    @Override
    public Match match(Applicability applicability, ScriptContext context) {
        return matcher.apply(applicability, context);
    }

    @Override
    public String defaultNamespace() {
        return defaultNamespace;
    }
}
