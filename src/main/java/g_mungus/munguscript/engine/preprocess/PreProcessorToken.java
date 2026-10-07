package g_mungus.munguscript.engine.preprocess;

import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

/**
 * A word a pre-processor rewrites, such as {@code @base} or an alias name.
 *
 * @param text        the token as written in a script, e.g. "@base"
 * @param placement   where in a command the token may stand
 * @param valueType   the type the token stands for once rewritten, so it is only offered where that
 *                    type fits. Null when it is not known; such a token is offered wherever its
 *                    placement allows.
 * @param description a short description for the suggestion list, e.g. the coordinates an address
 *                    points at
 */
public record PreProcessorToken(String text, Placement placement, @Nullable TypeKey valueType, String description) {

    public enum Placement {
        /** In place of an argument of {@code valueType}, as an address stands for coordinates. */
        ARGUMENT,
        /**
         * At the start of an expression, inside {@code value_of(...)} or after {@code if}, as an
         * alias stands for a getter and mappers. Offered where its type can be turned into the
         * one wanted.
         */
        EXPRESSION
    }
}
