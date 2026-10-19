package g_mungus.munguscript.engine.host;

import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A node meant only for some targets, or an executor meant for inside command executors. Suggestions
 * leave it out wherever the host says it does not apply ({@link Match#NONE}); parsing and running are
 * not affected. Inside a command executor's {@code command(...)}, only the executors that name it in
 * {@link #onlyInside} are suggested or parse; an executor with no restriction names none.
 *
 * <p>An engine lists its own ({@link g_mungus.munguscript.engine.ScriptEngine#restrictions}). A host
 * that sends its tree to a client sends these with it, serialising each {@link Applicability} its
 * own way, and the client gives them to {@link g_mungus.munguscript.engine.MungusScript#view}.
 *
 * @param name          the word scripts write for the node
 * @param input         for a mapper, the type it starts from; null otherwise
 * @param variant       for an executor, its place among the executors that share its name, in the
 *                      order they were registered; 0 otherwise
 * @param applicability which targets the node is meant for, or null for every target
 * @param onlyInside    for an executor, the command executors it is written inside, and only there;
 *                      empty for one written at the top of a command only
 */
public record Restriction(Kind kind, String name, @Nullable TypeKey input, int variant,
                          @Nullable Applicability applicability, Set<String> onlyInside) {

    public enum Kind { GETTER, MAPPER, EXECUTOR }

    public Restriction {
        onlyInside = Set.copyOf(onlyInside);
        if (applicability == null && onlyInside.isEmpty()) {
            throw new IllegalArgumentException("A restriction of '" + name + "' has to restrict something");
        }
    }

    public Restriction(Kind kind, String name, @Nullable TypeKey input, int variant, Applicability applicability) {
        this(kind, name, input, variant, applicability, Set.of());
    }

    public static Restriction getter(String name, Applicability applicability) {
        return new Restriction(Kind.GETTER, name, null, 0, applicability);
    }

    public static Restriction mapper(String name, TypeKey input, Applicability applicability) {
        return new Restriction(Kind.MAPPER, name, input, 0, applicability);
    }

    public static Restriction executor(String name, int variant, Applicability applicability) {
        return new Restriction(Kind.EXECUTOR, name, null, variant, applicability);
    }

    public static Restriction executor(String name, int variant, @Nullable Applicability applicability,
                                       Set<String> onlyInside) {
        return new Restriction(Kind.EXECUTOR, name, null, variant, applicability, onlyInside);
    }
}
