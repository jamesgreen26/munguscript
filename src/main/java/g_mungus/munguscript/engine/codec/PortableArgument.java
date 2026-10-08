package g_mungus.munguscript.engine.codec;

/**
 * A host's argument type that says how it reads, so a {@link PortableHostCodec} can write it for a
 * client that does not have the host's code, such as an editor. One that does not is read loosely,
 * as a word or a quoted string.
 *
 * <pre>{@code
 * // "a" to "b"
 * public ArgumentShape shape() {
 *     return new ArgumentShape.Sequence(List.of(new ArgumentShape.Text(ArgumentShape.Text.Kind.QUOTABLE),
 *             new ArgumentShape.Word("to"), new ArgumentShape.Text(ArgumentShape.Text.Kind.QUOTABLE)));
 * }
 * }</pre>
 */
public interface PortableArgument {

    ArgumentShape shape();
}
