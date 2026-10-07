package g_mungus.munguscript.engine_impl.tree;

import g_mungus.munguscript.language.type.TypeKey;

import java.util.Optional;

/**
 * The fixed names of the engine's own nodes. A view finds the tree again by these names wherever
 * it was sent, so they never change between builds. A chain node's name also says which type the
 * value has there, which is how a view knows the types of getters and mappers it has no registry
 * for.
 */
public final class NodeNames {
    private static final String PREFIX = "munguscript:";
    private static final char CHAIN_SEPARATOR = '/';

    /** Where a script command starts: executors, {@code if} and {@code unless}. */
    public static final String SCRIPT = PREFIX + "script";
    /** Where a condition starts: the getters that can lead to a boolean. */
    public static final String CONDITION = PREFIX + "condition";
    /** Where a {@code value_of(...)} expression starts: every getter. */
    public static final String VALUE = PREFIX + "value";

    public static final String IF = "if";
    public static final String UNLESS = "unless";
    public static final String ELSE = "else";

    private NodeNames() {
    }

    /** The node a {@code value_of} expression continues from when its value is of {@code type}. */
    public static String valueChain(TypeKey type) {
        return VALUE + CHAIN_SEPARATOR + type;
    }

    /** The node a condition continues from when its value is of {@code type}. */
    public static String conditionChain(TypeKey type) {
        return CONDITION + CHAIN_SEPARATOR + type;
    }

    public static boolean isValueChain(String name) {
        return name.startsWith(VALUE + CHAIN_SEPARATOR);
    }

    public static boolean isConditionChain(String name) {
        return name.startsWith(CONDITION + CHAIN_SEPARATOR);
    }

    /** The type a chain node holds, or empty if {@code name} is not a chain node's. */
    public static Optional<TypeKey> chainType(String name) {
        String key;
        if (isValueChain(name)) {
            key = name.substring(VALUE.length() + 1);
        } else if (isConditionChain(name)) {
            key = name.substring(CONDITION.length() + 1);
        } else {
            return Optional.empty();
        }
        int colon = key.indexOf(':');
        return colon <= 0 ? Optional.empty()
                : Optional.of(new TypeKey(key.substring(0, colon), key.substring(colon + 1)));
    }
}
