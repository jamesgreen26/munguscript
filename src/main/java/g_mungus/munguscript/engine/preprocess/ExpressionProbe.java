package g_mungus.munguscript.engine.preprocess;

import g_mungus.munguscript.language.type.TypeKey;

/** Checks expressions against the built tree without running them. */
public interface ExpressionProbe {

    /** Whether {@code expression} reads, in full, as something producing {@code type}. */
    boolean readsAs(String expression, TypeKey type);
}
