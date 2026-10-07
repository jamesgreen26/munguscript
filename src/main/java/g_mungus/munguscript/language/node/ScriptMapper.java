package g_mungus.munguscript.language.node;

import g_mungus.munguscript.language.type.ScriptType;

/** Turns one value into another. */
public non-sealed interface ScriptMapper<I, O> extends ScriptNode {

    ScriptType<I> inputType();

    ScriptType<O> outputType();

    O map(I input, ScriptContext context);
}
