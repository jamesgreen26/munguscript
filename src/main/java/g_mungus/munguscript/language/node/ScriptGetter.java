package g_mungus.munguscript.language.node;

import g_mungus.munguscript.language.type.ScriptType;

/** Reads a value from wherever the script runs. */
public non-sealed interface ScriptGetter<O> extends ScriptNode {

    ScriptType<O> outputType();

    O get(ScriptContext context);
}
