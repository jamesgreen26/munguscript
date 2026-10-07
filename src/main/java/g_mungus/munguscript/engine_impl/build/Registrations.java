package g_mungus.munguscript.engine_impl.build;

import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptMapper;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one build is made of, checked: the types, and the nodes with built-in and generated
 * mappers already merged in.
 *
 * @param nodes the host's nodes in registration order, then the built-in and generated ones it did
 *              not replace
 */
public record Registrations(List<ScriptType<?>> types, List<ScriptNode> nodes) {

    public Registrations {
        types = List.copyOf(types);
        nodes = List.copyOf(nodes);
    }

    public List<ScriptGetter<?>> getters() {
        List<ScriptGetter<?>> getters = new ArrayList<>();
        nodes.forEach(node -> {
            if (node instanceof ScriptGetter<?> getter) getters.add(getter);
        });
        return getters;
    }

    /** Mappers and argument mappers. */
    public List<ScriptNode> mappers() {
        return nodes.stream().filter(NodeTypes::isMapper).toList();
    }

    /** Executors grouped by name, in the order each name was first registered. */
    public List<ExecutorGroup> executors() {
        Map<String, List<ScriptExecutor<?, ?>>> byName = new LinkedHashMap<>();
        nodes.forEach(node -> {
            if (node instanceof ScriptExecutor<?, ?> executor) {
                byName.computeIfAbsent(executor.displayName(), name -> new ArrayList<>()).add(executor);
            }
        });
        return byName.entrySet().stream().map(entry -> new ExecutorGroup(entry.getKey(), entry.getValue())).toList();
    }

    /** The nodes that have an applicability, in registration order. */
    public List<Restriction> restrictions() {
        List<Restriction> restrictions = new ArrayList<>();
        Map<String, Integer> executorVariants = new HashMap<>();
        for (ScriptNode node : nodes) {
            // Every executor counts towards the variant numbers, restricted or not.
            int variant = node instanceof ScriptExecutor<?, ?>
                    ? executorVariants.merge(node.displayName(), 1, Integer::sum) - 1 : 0;
            if (node.applicability() == null) {
                continue;
            }
            restrictions.add(restriction(node, variant));
        }
        return restrictions;
    }

    private static Restriction restriction(ScriptNode node, int variant) {
        if (node instanceof ScriptGetter<?>) {
            return Restriction.getter(node.displayName(), node.applicability());
        } else if (node instanceof ScriptExecutor<?, ?>) {
            return Restriction.executor(node.displayName(), variant, node.applicability());
        } else if (node instanceof ScriptMapper<?, ?> || node instanceof ScriptArgumentMapper<?, ?, ?>) {
            return Restriction.mapper(node.displayName(), inputKey(node), node.applicability());
        }
        throw NodeTypes.unknownKind(node);
    }

    private static TypeKey inputKey(ScriptNode node) {
        return NodeTypes.input(node).orElseThrow().key();
    }
}
