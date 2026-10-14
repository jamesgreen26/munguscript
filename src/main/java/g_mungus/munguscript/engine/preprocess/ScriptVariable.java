package g_mungus.munguscript.engine.preprocess;

import g_mungus.munguscript.language.type.TypeKey;

/**
 * A {@code #var name = literal} line in a script: a value the host keeps between commands and can
 * change, written into each command as a literal when the command is pre-processed.
 *
 * <p>The host hands values over as plain text, as it received them, and any text is a value: like a
 * literal alias, a variable is whatever type its current value reads as. The engine writes the
 * literal.
 *
 * @param name    the name commands use it by
 * @param type    the primitive type of its declared literal, which suggestions show it as
 * @param initial its literal as declared, which it starts at and goes back to
 */
public record ScriptVariable(String name, TypeKey type, String initial) {
}
