package g_mungus.munguscript.language.type;

import org.jetbrains.annotations.Nullable;

/**
 * Whatever the host needs to create argument types while the tree is built. The engine only
 * passes it along. A Minecraft host would wrap a {@code CommandBuildContext} in it, so that types
 * such as block states can reach the registries.
 *
 * @param host the host's own object, or null when its argument types need nothing
 */
public record BuildEnvironment(@Nullable Object host) {

    public static final BuildEnvironment EMPTY = new BuildEnvironment(null);

    /** The host's own object, or an exception if it is not of {@code type}. */
    public <H> H unwrap(Class<H> type) {
        if (!type.isInstance(host)) {
            throw new IllegalStateException("The build environment holds "
                    + (host == null ? "nothing" : host.getClass().getName()) + ", not " + type.getName());
        }
        return type.cast(host);
    }
}
