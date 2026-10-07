package g_mungus.munguscript.engine.host;

import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

/**
 * A node meant only for some targets. Suggestions leave it out wherever the host says it does not
 * apply ({@link Match#NONE}); parsing and running are not affected.
 *
 * <p>An engine lists its own ({@link g_mungus.munguscript.engine.ScriptEngine#restrictions}). A host
 * that sends its tree to a client sends these with it, serialising each {@link Applicability} its
 * own way, and the client gives them to {@link g_mungus.munguscript.engine.MungusScript#view}.
 *
 * @param name          the word scripts write for the node
 * @param input         for a mapper, the type it starts from; null otherwise
 * @param variant       for an executor, its place among the executors that share its name, in the
 *                      order they were registered; 0 otherwise
 * @param applicability which targets the node is meant for
 */
public record Restriction(Kind kind, String name, @Nullable TypeKey input, int variant, Applicability applicability) {

    public enum Kind { GETTER, MAPPER, EXECUTOR }

    public static Restriction getter(String name, Applicability applicability) {
        return new Restriction(Kind.GETTER, name, null, 0, applicability);
    }

    public static Restriction mapper(String name, TypeKey input, Applicability applicability) {
        return new Restriction(Kind.MAPPER, name, input, 0, applicability);
    }

    public static Restriction executor(String name, int variant, Applicability applicability) {
        return new Restriction(Kind.EXECUTOR, name, null, variant, applicability);
    }
}
