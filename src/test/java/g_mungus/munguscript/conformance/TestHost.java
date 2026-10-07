package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A host with nothing behind it. A source is a place (where the script runs, what block it faces,
 * and the origin relative coordinates count from) plus the engine's run state.
 */
final class TestHost implements ScriptHost<TestHost.Source> {

    static final TestHost INSTANCE = new TestHost();

    /** The client's side: it parses and suggests but never runs, so it has no run state. It answers as {@link #INSTANCE} does. */
    static final ScriptViewHost<Source> VIEW_ONLY = new ScriptViewHost<>() {
        @Override
        public Object hostContext(Source source) {
            return INSTANCE.hostContext(source);
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            return INSTANCE.match(applicability, context);
        }

        @Override
        public String defaultNamespace() {
            return INSTANCE.defaultNamespace();
        }
    };

    record Place(Point pos, Point origin, String block) {
    }

    record Source(Place place, @Nullable RunState state) {
    }

    /** Meant for these blocks. */
    record Blocks(Set<String> ids) implements Applicability {
        static Blocks of(String... ids) {
            return new Blocks(Set.of(ids));
        }
    }

    @Override
    public @Nullable RunState runState(Source source) {
        return source.state();
    }

    @Override
    public Source withRunState(Source source, RunState state) {
        return new Source(source.place(), state);
    }

    @Override
    public Object hostContext(Source source) {
        return source.place();
    }

    @Override
    public Match match(Applicability applicability, ScriptContext context) {
        return applicability instanceof Blocks blocks && blocks.ids().contains(context.host(Place.class).block())
                ? Match.EXPLICIT : Match.NONE;
    }

    @Override
    public String defaultNamespace() {
        return "host";
    }
}
