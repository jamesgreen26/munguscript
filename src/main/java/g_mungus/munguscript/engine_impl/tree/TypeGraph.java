package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which types mappers, and conversions, can turn which other types into. Used to leave out of the tree, and out of
 * suggestions, whatever cannot lead to the type wanted, and to explain a {@code value_of} that
 * gives the wrong type.
 */
public final class TypeGraph {
    private final Map<TypeKey, Set<TypeKey>> edges;

    private TypeGraph(Map<TypeKey, Set<TypeKey>> edges) {
        this.edges = edges;
    }

    /** A graph with an edge from each mapper's input type to its output type. */
    public static TypeGraph of(Collection<Edge> mappers) {
        Map<TypeKey, Set<TypeKey>> edges = new HashMap<>();
        for (Edge edge : mappers) {
            edges.computeIfAbsent(edge.from(), key -> new HashSet<>()).add(edge.to());
        }
        return new TypeGraph(edges);
    }

    /** The graph a tree's value chains describe. */
    public static TypeGraph of(ScriptTree<?> tree) {
        Map<TypeKey, Set<TypeKey>> edges = new HashMap<>();
        tree.valueChains().forEach((type, chain) -> {
            Set<TypeKey> outputs = edges.computeIfAbsent(type, key -> new HashSet<>());
            for (CommandNode<?> mapper : chain.getChildren()) {
                ScriptTree.outputOf(mapper).ifPresent(outputs::add);
            }
        });
        return new TypeGraph(edges);
    }

    /** This graph with {@code more} edges, such as those of the conversions between types. */
    public TypeGraph with(Collection<Edge> more) {
        Map<TypeKey, Set<TypeKey>> all = new HashMap<>();
        edges.forEach((from, to) -> all.put(from, new HashSet<>(to)));
        more.forEach(edge -> all.computeIfAbsent(edge.from(), key -> new HashSet<>()).add(edge.to()));
        return new TypeGraph(all);
    }

    /** Whether a value of {@code from} is, or can be mapped into, {@code to}. */
    public boolean reaches(TypeKey from, TypeKey to) {
        Set<TypeKey> seen = new HashSet<>();
        Deque<TypeKey> pending = new ArrayDeque<>();
        pending.add(from);
        while (!pending.isEmpty()) {
            TypeKey type = pending.poll();
            if (type.equals(to)) {
                return true;
            }
            if (seen.add(type)) {
                pending.addAll(edges.getOrDefault(type, Set.of()));
            }
        }
        return false;
    }

    public boolean reachesAny(TypeKey from, Collection<TypeKey> targets) {
        return targets.stream().anyMatch(target -> reaches(from, target));
    }

    /** One mapper, as far as types go. */
    public record Edge(TypeKey from, TypeKey to) {
    }
}
