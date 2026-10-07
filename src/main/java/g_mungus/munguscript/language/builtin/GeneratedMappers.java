package g_mungus.munguscript.language.builtin;

import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The mappers every type that scripts can write gets for free: {@code as_string},
 * {@code as_<type>} from a string, and {@code ==} against a value of the same type. A mapper the
 * host registered with the same name and input type wins.
 *
 * <p>Unlike {@link g_mungus.munguscript.language.builtin.BuiltInMappers}, these depend on which types
 * are registered, so they are made for each build.
 */
public final class GeneratedMappers {

    private GeneratedMappers() {
    }

    /** The mappers generated for {@code type}: none if it is opaque. */
    public static <T> List<ScriptNode> forType(ScriptType<T> type) {
        ScriptType.Literal<T, ?> literal = type.literal().orElse(null);
        if (literal == null) {
            return List.of();
        }
        List<ScriptNode> mappers = new ArrayList<>();
        if (type != BuiltInTypes.STRING) {
            mappers.add(ScriptNodes.mapper(
                    "as_string", type, BuiltInTypes.STRING, (value, context) -> literal.print(value)));
            mappers.add(ScriptNodes.mapper(
                    "as_" + type.key().path(), BuiltInTypes.STRING, type, (text, context) -> literal.parse(text)));
        }
        mappers.add(ScriptNodes.argumentMapper(
                "==", type, BuiltInTypes.BOOLEAN, type,
                (value, context) -> Objects.equals(value, context.argumentValue(type.javaClass()))));
        return mappers;
    }
}
