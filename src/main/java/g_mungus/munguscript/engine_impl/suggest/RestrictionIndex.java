package g_mungus.munguscript.engine_impl.suggest;

import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine_impl.run.Overloads;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The restricted nodes a view was given, looked up by where they stand in the tree, to leave out of
 * suggestions what is not meant for the target. A node not listed is meant for every target.
 */
public final class RestrictionIndex<S> {
    private final ScriptViewHost<S> host;
    private final Map<Key, Applicability> applicabilities = new HashMap<>();

    public RestrictionIndex(ScriptViewHost<S> host, Collection<Restriction> restrictions) {
        this.host = host;
        for (Restriction restriction : restrictions) {
            applicabilities.put(new Key(restriction.kind(), restriction.name(), restriction.input(), restriction.variant()),
                    restriction.applicability());
        }
    }

    public boolean getterApplies(String name, S source) {
        return applies(new Key(Restriction.Kind.GETTER, name, null, 0), source);
    }

    public boolean mapperApplies(String name, TypeKey input, S source) {
        return applies(new Key(Restriction.Kind.MAPPER, name, input, 0), source);
    }

    public boolean variantApplies(String executor, int variant, S source) {
        return applies(new Key(Restriction.Kind.EXECUTOR, executor, null, variant), source);
    }

    /** Whether any of the executors sharing a name applies. */
    public boolean executorApplies(String executor, int variants, S source) {
        for (int variant = 0; variant < variants; variant++) {
            if (variantApplies(executor, variant, source)) {
                return true;
            }
        }
        return false;
    }

    private boolean applies(Key key, S source) {
        return Overloads.match(host, applicabilities.get(key), source) != Match.NONE;
    }

    private record Key(Restriction.Kind kind, String name, @Nullable TypeKey input, int variant) {
    }
}
