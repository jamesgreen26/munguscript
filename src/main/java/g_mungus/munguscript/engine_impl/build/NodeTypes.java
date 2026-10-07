package g_mungus.munguscript.engine_impl.build;

import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptMapper;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The types each kind of node reads and gives, so the rest of the build need not switch on node kinds. */
public final class NodeTypes {

    private NodeTypes() {
    }

    /** The type a mapper or executor takes in; empty for a getter. */
    public static Optional<ScriptType<?>> input(ScriptNode node) {
        if (node instanceof ScriptGetter<?>) {
            return Optional.empty();
        } else if (node instanceof ScriptMapper<?, ?> mapper) {
            return Optional.of(mapper.inputType());
        } else if (node instanceof ScriptArgumentMapper<?, ?, ?> mapper) {
            return Optional.of(mapper.inputType());
        } else if (node instanceof ScriptExecutor<?, ?> executor) {
            return Optional.of(executor.inputType());
        }
        throw unknownKind(node);
    }

    /** The type a getter or mapper gives; empty for an executor. */
    public static Optional<ScriptType<?>> output(ScriptNode node) {
        if (node instanceof ScriptGetter<?> getter) {
            return Optional.of(getter.outputType());
        } else if (node instanceof ScriptMapper<?, ?> mapper) {
            return Optional.of(mapper.outputType());
        } else if (node instanceof ScriptArgumentMapper<?, ?, ?> mapper) {
            return Optional.of(mapper.outputType());
        } else if (node instanceof ScriptExecutor<?, ?>) {
            return Optional.empty();
        }
        throw unknownKind(node);
    }

    /** Every type a node uses, each of which must be registered. */
    public static List<ScriptType<?>> used(ScriptNode node) {
        List<ScriptType<?>> types = new ArrayList<>();
        input(node).ifPresent(types::add);
        output(node).ifPresent(types::add);
        if (node instanceof ScriptArgumentMapper<?, ?, ?> mapper && mapper.argumentScriptType() != null) {
            types.add(mapper.argumentScriptType());
        }
        return types;
    }

    public static boolean isMapper(ScriptNode node) {
        if (node instanceof ScriptMapper<?, ?> || node instanceof ScriptArgumentMapper<?, ?, ?>) {
            return true;
        } else if (node instanceof ScriptGetter<?> || node instanceof ScriptExecutor<?, ?>) {
            return false;
        }
        throw unknownKind(node);
    }

    /**
     * Thrown where a node is none of {@link ScriptNode}'s permitted kinds. That cannot happen while the
     * interface stays sealed, but on Java 17 the compiler cannot check these if-chains are exhaustive.
     */
    public static IllegalStateException unknownKind(ScriptNode node) {
        return new IllegalStateException("Unknown node kind: " + node.getClass().getName());
    }

    /** What identifies a mapper: its name and the type it starts from. */
    public record MapperKey(String name, TypeKey input) {
        public static Optional<MapperKey> of(ScriptNode node) {
            return isMapper(node)
                    ? Optional.of(new MapperKey(node.displayName(), NodeTypes.input(node).orElseThrow().key()))
                    : Optional.empty();
        }
    }
}
