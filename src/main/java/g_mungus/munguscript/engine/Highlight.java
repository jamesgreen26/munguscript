package g_mungus.munguscript.engine;

import com.mojang.brigadier.context.StringRange;

/**
 * What one word of a command is, for syntax highlighting: see {@link ScriptView#highlight}.
 *
 * @param range where the word is in the command as the player wrote it
 */
public record Highlight(StringRange range, Kind kind) {

    public enum Kind {
        /** {@code if}, {@code unless} or {@code else}. */
        KEYWORD,
        /** The word that starts a command, or a branch of one, and acts on the target. */
        EXECUTOR,
        /** The first word of an expression, which reads a value. */
        GETTER,
        /** A word that turns the value before it into another. */
        MAPPER,
        /**
         * What an executor or mapper takes: a literal value, or a pre-processor token that stands
         * for one, such as an address. For a {@code value_of(...)}, only {@code value_of(} and its
         * closing {@code )}: the expression inside is highlighted word by word.
         */
        ARGUMENT,
        /** An alias, or another pre-processor token that stands for an expression. */
        ALIAS,
        /** Text the command could not be read as, from where reading stopped. */
        UNPARSED
    }
}
