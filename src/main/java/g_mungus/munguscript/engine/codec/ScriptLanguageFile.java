package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.MungusScript;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A whole language in one file, which a tool without the host's code can read scripts by, such as
 * an editor: the default namespace, each of the host's types, and the tree, written by a
 * {@link ScriptTreeCodec} with a {@link PortableHostCodec}.
 *
 * <p>A type is written as its key and the types it is usable as: that is all a view needs of it.
 * The view read back has a stand-in for each, which holds no values. Like any decoded view, it
 * parses, suggests and highlights, and never runs anything.
 *
 * <pre>{@code
 * // the host, once its engine is grafted under `graftedUnder`
 * new ScriptLanguageFile().write(engine, graftedUnder, "my_app", out);
 * // the tool
 * ScriptView<SimpleSource> view = new ScriptLanguageFile().read(in, SimpleHost::new);
 * }</pre>
 */
public final class ScriptLanguageFile {
    /** What a language file starts with: "MGSL". */
    public static final int MAGIC = 0x4D47534C;
    /** The layout {@link #write} writes and {@link #read} reads, around the tree's own format. */
    public static final int FORMAT_VERSION = 2;

    private final ScriptTreeCodec codec;

    /** For the engine {@link MungusScript} builds. */
    public ScriptLanguageFile() {
        this(MungusScript.arguments());
    }

    /**
     * For the engine {@link MungusScript} builds, with the shapes of argument types the host cannot
     * make {@link PortableArgument}s, such as another library's ({@link PortableHostCodec}).
     */
    public ScriptLanguageFile(Function<ArgumentType<?>, @Nullable ArgumentShape> shapes) {
        this(MungusScript.arguments(), shapes);
    }

    /** For the engine whose argument types {@code arguments} describes and rebuilds. */
    public ScriptLanguageFile(ScriptArguments arguments) {
        this(arguments, type -> null);
    }

    public ScriptLanguageFile(ScriptArguments arguments, Function<ArgumentType<?>, @Nullable ArgumentShape> shapes) {
        this.codec = new ScriptTreeCodec(arguments, new PortableHostCodec(shapes));
    }

    /**
     * Writes {@code engine}'s language: its tree under {@code graftedUnder}, the node it was grafted
     * under, and its types.
     *
     * @param defaultNamespace the namespace type keys are written without, as the host's
     */
    public <S> void write(ScriptEngine<S> engine, CommandNode<S> graftedUnder, String defaultNamespace,
                          DataOutput out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeUTF(defaultNamespace);
        Set<TypeKey> builtIn = BuiltInTypes.ALL.stream().map(ScriptType::key).collect(Collectors.toSet());
        List<ScriptType<?>> types = engine.types().stream().filter(type -> !builtIn.contains(type.key())).toList();
        out.writeInt(types.size());
        for (ScriptType<?> type : types) {
            out.writeUTF(type.key().toString());
            out.writeInt(type.conversions().size());
            for (ScriptType.Conversion<?, ?> conversion : type.conversions()) {
                out.writeUTF(conversion.target().key().toString());
            }
        }
        codec.encode(engine, graftedUnder, out);
    }

    /**
     * Reads a language {@link #write} wrote, as a view over its tree.
     *
     * @param host the view's host, given the default namespace the language was written with
     * @throws IOException if the bytes are not a language file this format describes
     */
    public <S> ScriptView<S> read(DataInput in, Function<String, ScriptViewHost<S>> host) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("Not a MungusScript language file");
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Language file format " + version + " cannot be read; this library reads format "
                    + FORMAT_VERSION);
        }
        String defaultNamespace = in.readUTF();
        Map<TypeKey, List<TypeKey>> declared = new LinkedHashMap<>();
        int typeCount = count(in, "types");
        for (int i = 0; i < typeCount; i++) {
            TypeKey key = readKey(in);
            List<TypeKey> targets = new ArrayList<>();
            for (int j = count(in, "conversions of " + key); j > 0; j--) {
                targets.add(readKey(in));
            }
            declared.put(key, targets);
        }
        return codec.decode(in, host.apply(defaultNamespace), standIns(declared));
    }

    /** A type for each key, holding nothing, usable as what the key's type was. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<ScriptType<?>> standIns(Map<TypeKey, List<TypeKey>> declared) throws IOException {
        Map<TypeKey, ScriptType<?>> known = new LinkedHashMap<>();
        BuiltInTypes.ALL.forEach(type -> known.put(type.key(), type));
        for (Map.Entry<TypeKey, List<TypeKey>> entry : declared.entrySet()) {
            for (TypeKey target : entry.getValue()) {
                if (!declared.containsKey(target) && !known.containsKey(target)) {
                    throw new IOException("Type " + entry.getKey() + " is usable as " + target + ", which is not a type");
                }
            }
        }
        // Each conversion finds its target once all are made, since types may be usable as each other.
        Map<TypeKey, ScriptType<?>> made = new LinkedHashMap<>();
        for (Map.Entry<TypeKey, List<TypeKey>> entry : declared.entrySet()) {
            ScriptType type = ScriptType.opaque(entry.getKey(), Object.class);
            for (TypeKey target : entry.getValue()) {
                type = type.usableAs(() -> made.getOrDefault(target, known.get(target)), Function.identity());
            }
            made.put(entry.getKey(), type);
        }
        return List.copyOf(made.values());
    }

    private static TypeKey readKey(DataInput in) throws IOException {
        String text = in.readUTF();
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IOException("'" + text + "' is not a type key");
        }
        return new TypeKey(text.substring(0, colon), text.substring(colon + 1));
    }

    private static int count(DataInput in, String what) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative count of " + what + ": " + count);
        }
        return count;
    }
}
