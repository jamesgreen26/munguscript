package g_mungus.munguscript.engine_impl.tree;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.TypeKey;

/**
 * Types as scripts and errors write them: by path in the engine's namespace and the host's default
 * one, in full otherwise.
 */
public record TypeNames(String defaultNamespace) {

    public String of(TypeKey key) {
        boolean short_ = key.namespace().equals(BuiltInTypes.NAMESPACE) || key.namespace().equals(defaultNamespace);
        return short_ ? key.path() : key.toString();
    }
}
