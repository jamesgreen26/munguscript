package g_mungus.munguscript.engine_impl.build;

import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.builtin.BuiltInMappers;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.builtin.GeneratedMappers;
import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptMapper;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collects one build's registrations and checks them. Used once: {@link #finish} merges in the
 * built-in and generated mappers the host did not replace.
 */
public final class Registry implements ScriptRegistrar {
    private final Map<TypeKey, ScriptType<?>> types = new LinkedHashMap<>();
    private final List<ScriptNode> nodes = new ArrayList<>();

    public Registry() {
        BuiltInTypes.ALL.forEach(type -> types.put(type.key(), type));
    }

    @Override
    public <T> ScriptType<T> registerType(ScriptType<T> type) {
        if (types.putIfAbsent(type.key(), type) != null) {
            throw new IllegalStateException("Script type " + type.key() + " is already registered");
        }
        return type;
    }

    @Override
    public void register(ScriptNode node) {
        nodes.add(Objects.requireNonNull(node, "node"));
    }

    /**
     * @throws IllegalStateException if a node uses an unregistered type, or two of the host's nodes
     *                               would be the same word in the same place
     */
    public Registrations finish() {
        Set<String> getters = new HashSet<>();
        Set<NodeTypes.MapperKey> mappers = new HashSet<>();
        for (ScriptNode node : nodes) {
            checkTypes(node);
            checkUnique(node, getters, mappers);
        }
        List<ScriptNode> all = new ArrayList<>(nodes);
        for (ScriptNode standard : standardMappers()) {
            // A host mapper with the same name and input type replaces the standard one.
            if (!mappers.contains(NodeTypes.MapperKey.of(standard).orElseThrow())) {
                all.add(standard);
            }
        }
        return new Registrations(List.copyOf(types.values()), all);
    }

    private void checkTypes(ScriptNode node) {
        for (ScriptType<?> type : NodeTypes.used(node)) {
            if (!types.containsKey(type.key())) {
                throw new IllegalStateException("Node '" + node.displayName() + "' uses " + type.key()
                        + ", which is not registered");
            }
        }
    }

    private static void checkUnique(ScriptNode node, Set<String> getters, Set<NodeTypes.MapperKey> mappers) {
        String name = node.displayName();
        boolean unique;
        if (node instanceof ScriptGetter<?>) {
            unique = getters.add(name);
        } else if (node instanceof ScriptExecutor<?, ?>) {
            unique = !ScriptTree.isKeyword(name);
        } else if (node instanceof ScriptMapper<?, ?> || node instanceof ScriptArgumentMapper<?, ?, ?>) {
            unique = mappers.add(NodeTypes.MapperKey.of(node).orElseThrow());
        } else {
            throw NodeTypes.unknownKind(node);
        }
        if (!unique) {
            throw new IllegalStateException("'" + name + "' is already a word scripts use in the same place");
        }
    }

    /** The built-in mappers, and the ones generated for every writable type of this build. */
    private List<ScriptNode> standardMappers() {
        List<ScriptNode> standard = new ArrayList<>(BuiltInMappers.ALL);
        types.values().forEach(type -> standard.addAll(GeneratedMappers.forType(type)));
        return standard;
    }
}
