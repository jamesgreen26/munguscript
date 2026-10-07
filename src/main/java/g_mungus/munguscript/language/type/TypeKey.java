package g_mungus.munguscript.language.type;

/**
 * A script type's identity, such as {@code zps:int}. Two types with the same Java class are told
 * apart by key: {@code vec_pos} and {@code vec_dir} are both vectors.
 *
 * <p>Scripts and errors write a key by path alone when it is in the built-in namespace or the
 * host's default one, and in full otherwise.
 */
public record TypeKey(String namespace, String path) {
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
