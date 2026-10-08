package g_mungus.munguscript.engine_impl.tree;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Which types each type is usable as, and how a value becomes one: the conversions the types
 * declare, and one to a string for every type that does not declare its own. A value goes the
 * shortest way there. Two ways of the same length are refused, since they could give different
 * values.
 *
 * <p>Made from the types alone, so an engine and a view over a tree it sent, whose host registers
 * the same types, agree.
 */
public final class Conversions {
    private static final TypeKey STRING = BuiltInTypes.STRING.key();

    /** For each type, the types it is usable as, nearest first. */
    private final Map<TypeKey, Map<TypeKey, Path>> paths;

    private Conversions(Map<TypeKey, Map<TypeKey, Path>> paths) {
        this.paths = paths;
    }

    /**
     * @throws IllegalStateException if a type is usable as one that is not among {@code types}, or
     *                               as the same type in two ways
     */
    public static Conversions of(Collection<ScriptType<?>> types) {
        Map<TypeKey, List<Edge>> direct = new LinkedHashMap<>();
        types.forEach(type -> direct.put(type.key(), declared(type)));
        direct.forEach((from, edges) -> edges.forEach(edge -> {
            if (edge.to().equals(from)) {
                throw new IllegalStateException("Script type " + from + " cannot be usable as itself");
            }
            if (!direct.containsKey(edge.to())) {
                throw new IllegalStateException("Script type " + from + " is usable as " + edge.to()
                        + ", which is not registered");
            }
        }));
        Map<TypeKey, Map<TypeKey, Path>> paths = new HashMap<>();
        direct.keySet().forEach(from -> paths.put(from, shortest(from, direct)));
        return new Conversions(paths);
    }

    /** The types a {@code from} is usable as, nearest first. */
    public List<TypeKey> usableAs(TypeKey from) {
        return List.copyOf(from(from).keySet());
    }

    /** Whether a {@code from} is, or is usable as, a {@code to}. */
    public boolean converts(TypeKey from, TypeKey to) {
        return from.equals(to) || from(from).containsKey(to);
    }

    /**
     * Which of {@code targets} a {@code from} is best used as: itself if it is one, otherwise the
     * nearest it is usable as, the earliest of {@code targets} among those as near. A string, which
     * everything is usable as, only if it is usable as none of the others.
     */
    public Optional<TypeKey> best(TypeKey from, List<TypeKey> targets) {
        if (targets.contains(from)) {
            return Optional.of(from);
        }
        Optional<TypeKey> nearest = targets.stream()
                .filter(target -> !target.equals(STRING) && from(from).containsKey(target))
                .min(Comparator.comparingInt(target -> from(from).get(target).length()));
        return nearest.isPresent() || !targets.contains(STRING) || !converts(from, STRING)
                ? nearest : Optional.of(STRING);
    }

    /** Turns {@code value}, a {@code from}, into a {@code to}. */
    public @Nullable Object convert(@Nullable Object value, TypeKey from, TypeKey to) {
        if (from.equals(to)) {
            return value;
        }
        Path path = from(from).get(to);
        if (path == null) {
            throw new IllegalArgumentException(from + " is not usable as " + to);
        }
        return path.convert().apply(value);
    }

    /** An edge from each type to each type it is usable as. */
    public List<TypeGraph.Edge> edges() {
        List<TypeGraph.Edge> edges = new ArrayList<>();
        paths.forEach((from, to) -> to.keySet().forEach(target -> edges.add(new TypeGraph.Edge(from, target))));
        return edges;
    }

    private Map<TypeKey, Path> from(TypeKey from) {
        return paths.getOrDefault(from, Map.of());
    }

    /** Breadth first, so each type is reached the shortest way, and a second way as short is seen. */
    private static Map<TypeKey, Path> shortest(TypeKey from, Map<TypeKey, List<Edge>> direct) {
        Map<TypeKey, Path> found = new LinkedHashMap<>();
        Set<TypeKey> seen = new HashSet<>(Set.of(from));
        Map<TypeKey, Path> layer = Map.of(from, new Path(0, Function.identity()));
        while (!layer.isEmpty()) {
            Map<TypeKey, Path> next = new LinkedHashMap<>();
            Map<TypeKey, TypeKey> via = new HashMap<>();
            for (Map.Entry<TypeKey, Path> reached : layer.entrySet()) {
                for (Edge edge : direct.get(reached.getKey())) {
                    if (seen.contains(edge.to())) {
                        continue;
                    }
                    TypeKey other = via.putIfAbsent(edge.to(), reached.getKey());
                    if (other != null) {
                        throw new IllegalStateException("Script type " + from + " is usable as " + edge.to()
                                + " in two ways, through " + other + " and through " + reached.getKey());
                    }
                    next.put(edge.to(), reached.getValue().then(edge.convert()));
                }
            }
            seen.addAll(next.keySet());
            found.putAll(next);
            layer = next;
        }
        return found;
    }

    /** What {@code type} declares it is usable as, and a string if it does not say how. */
    private static <T> List<Edge> declared(ScriptType<T> type) {
        List<Edge> edges = new ArrayList<>();
        for (ScriptType.Conversion<T, ?> conversion : type.conversions()) {
            edges.add(new Edge(conversion.target().key(), erase(type, conversion.convert())));
        }
        if (!type.key().equals(STRING) && edges.stream().noneMatch(edge -> edge.to().equals(STRING))) {
            edges.add(new Edge(STRING, erase(type, printed(type))));
        }
        return edges;
    }

    /** A writable type's printed form, which reads back as one; anything else's {@code toString}. */
    private static <T> Function<T, String> printed(ScriptType<T> type) {
        return type.literal().<Function<T, String>>map(literal -> literal::print).orElse(String::valueOf);
    }

    private static <T> Function<Object, Object> erase(ScriptType<T> type, Function<T, ?> convert) {
        return value -> convert.apply(type.javaClass().cast(value));
    }

    private record Edge(TypeKey to, Function<Object, Object> convert) {
    }

    private record Path(int length, Function<Object, Object> convert) {
        Path then(Function<Object, Object> next) {
            return new Path(length + 1, convert.andThen(next));
        }
    }
}
