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
import java.util.Set;

/**
 * The restricted nodes a view was given, looked up by where they stand in the tree, to leave out of
 * suggestions what is not meant for the target, and to tell which executors may be written inside a
 * command executor. A node not listed is meant for every target, and for the top of a command only.
 */
public final class RestrictionIndex<S> {
    private final ScriptViewHost<S> host;
    private final Map<Key, Restriction> restrictions = new HashMap<>();

    public RestrictionIndex(ScriptViewHost<S> host, Collection<Restriction> restrictions) {
        this.host = host;
        for (Restriction restriction : restrictions) {
            this.restrictions.put(new Key(restriction.kind(), restriction.name(), restriction.input(),
                    restriction.variant()), restriction);
        }
    }

    public boolean getterApplies(String name, S source) {
        return applies(new Key(Restriction.Kind.GETTER, name, null, 0), source);
    }

    public boolean mapperApplies(String name, TypeKey input, S source) {
        return applies(new Key(Restriction.Kind.MAPPER, name, input, 0), source);
    }

    /**
     * @param inside the command executor whose {@code command(...)} the executor would be written in, or
     *               null at the top of a command
     */
    public boolean variantApplies(String executor, int variant, S source, @Nullable String inside) {
        Key key = new Key(Restriction.Kind.EXECUTOR, executor, null, variant);
        return writtenIn(key, inside) && applies(key, source);
    }

    /**
     * Whether any of the executors sharing a name is meant for inside {@code inside}'s {@code command(...)}, the
     * only executors that parse there.
     */
    public boolean executorParsesInside(String executor, int variants, String inside) {
        for (int variant = 0; variant < variants; variant++) {
            if (writtenIn(new Key(Restriction.Kind.EXECUTOR, executor, null, variant), inside)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inside a command executor, only an executor that names it; at the top of a command, any executor not meant
     * for inside one.
     */
    private boolean writtenIn(Key key, @Nullable String inside) {
        Restriction restriction = restrictions.get(key);
        Set<String> onlyInside = restriction == null ? Set.of() : restriction.onlyInside();
        return inside == null ? onlyInside.isEmpty() : onlyInside.contains(inside);
    }

    /** Whether any of the executors sharing a name applies. */
    public boolean executorApplies(String executor, int variants, S source, @Nullable String inside) {
        for (int variant = 0; variant < variants; variant++) {
            if (variantApplies(executor, variant, source, inside)) {
                return true;
            }
        }
        return false;
    }

    private boolean applies(Key key, S source) {
        Restriction restriction = restrictions.get(key);
        Applicability applicability = restriction == null ? null : restriction.applicability();
        return Overloads.match(host, applicability, source) != Match.NONE;
    }

    private record Key(Restriction.Kind kind, String name, @Nullable TypeKey input, int variant) {
    }
}
