package g_mungus.munguscript.engine.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.List;

/**
 * What one of the engine's own argument types is made of: everything a host has to send for a
 * client to {@linkplain ScriptArguments#rebuild rebuild} it. The host serialises the wrapped
 * argument types the way it does any other, and the type keys as text.
 */
public sealed interface ArgumentDescription {

    /**
     * An argument that reads {@code literal}, a {@code value_of(...)} giving {@code target}, or a
     * {@code %s} placeholder.
     */
    record ValueOrLiteral(ArgumentType<?> literal, TypeKey target) implements ArgumentDescription {
    }

    /**
     * A {@code literal_of(...)} at the start of an expression: reads {@code literal}, the argument of
     * the primitive type {@code type}, between the brackets.
     */
    record LiteralOf(ArgumentType<?> literal, TypeKey type) implements ArgumentDescription {
    }

    /** The argument of executors that share a name: one variant per executor, in order. */
    record Overloaded(List<ValueOrLiteral> variants) implements ArgumentDescription {
        public Overloaded {
            variants = List.copyOf(variants);
        }
    }
}
