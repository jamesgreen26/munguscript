package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.munguscript.engine.MungusScript;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.argument.ArgumentDescription;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encodes the engine's part of a command tree on a server, and decodes it on a client into a
 * {@link ScriptView}, so a host never writes a tree codec of its own. The host only writes its own
 * objects, through a {@link HostCodec}, and moves the bytes.
 *
 * <pre>{@code
 * // server, once the engine is grafted under `graftedUnder`
 * new ScriptTreeCodec(hostCodec).encode(engine, graftedUnder, out);
 * // client
 * ScriptView<S> view = new ScriptTreeCodec(hostCodec).decode(in, viewHost, types);
 * // then redirect a prefix of the client's own to view.scriptRoot()
 * }</pre>
 *
 * <p>What is written: a format version; every node under the graft, numbered by identity and never
 * merged, with its kind, name, whether it runs, its children in order and its redirect; each
 * argument node's type, as the engine {@linkplain ScriptArguments#describe describes} it or
 * through the host; and the engine's {@linkplain ScriptEngine#restrictions restrictions}. Commands
 * and redirect modifiers are not written: a decoded tree only parses and suggests. Script types are
 * not written either: they are code, which the client registers itself.
 *
 * <p>The codec only uses the engine's API, so it works with any engine.
 */
public final class ScriptTreeCodec {
    /** The format {@link #encode} writes and {@link #decode} reads. */
    public static final int FORMAT_VERSION = 1;

    private static final byte LITERAL = 0;
    private static final byte ARGUMENT = 1;
    private static final byte ROOT = 2;

    private static final byte HOST_ARGUMENT = 0;
    private static final byte VALUE_OR_LITERAL = 1;
    private static final byte OVERLOADED = 2;

    private final ScriptArguments arguments;
    private final HostCodec host;

    /** A codec for the engine {@link MungusScript} builds. */
    public ScriptTreeCodec(HostCodec host) {
        this(MungusScript.arguments(), host);
    }

    /** A codec for the engine whose argument types {@code arguments} describes and rebuilds. */
    public ScriptTreeCodec(ScriptArguments arguments, HostCodec host) {
        this.arguments = arguments;
        this.host = host;
    }

    /**
     * Writes the tree under {@code graftedUnder}, the node {@code engine} was grafted under, and the
     * engine's restrictions.
     *
     * @throws IllegalArgumentException if the tree holds a kind of node Brigadier does not make
     */
    public <S> void encode(ScriptEngine<S> engine, CommandNode<S> graftedUnder, DataOutput out) throws IOException {
        List<CommandNode<S>> nodes = number(graftedUnder);
        Map<CommandNode<S>, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            index.put(nodes.get(i), i);
        }
        out.writeInt(FORMAT_VERSION);
        out.writeInt(nodes.size());
        for (CommandNode<S> node : nodes) {
            writeNode(node, index, out);
        }
        List<Restriction> restrictions = engine.restrictions();
        out.writeInt(restrictions.size());
        for (Restriction restriction : restrictions) {
            writeRestriction(restriction, out);
        }
    }

    /**
     * Reads a tree {@link #encode} wrote, and makes the view over it, as
     * {@link MungusScript#view} would over the server's own tree.
     *
     * @param types the host's own script types; the built-in ones are always known
     * @throws IOException if the bytes are not a tree this format describes
     */
    public <S> ScriptView<S> decode(DataInput in, ScriptViewHost<S> viewHost, Collection<ScriptType<?>> types)
            throws IOException {
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Script tree format " + version + " cannot be read; this library reads format "
                    + FORMAT_VERSION);
        }
        int count = in.readInt();
        if (count < 1) {
            throw new IOException("A script tree needs at least the node it was grafted under, not " + count);
        }
        ScriptArguments.Rebuild rebuild = arguments.rebuild();
        // Grown as nodes are read, so a count that the bytes do not back runs out rather than
        // reserving room for it.
        List<NodeRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(readNode(in, i, count, rebuild));
        }
        int restrictionCount = readCount(in, "restrictions");
        List<Restriction> restrictions = new ArrayList<>();
        for (int i = 0; i < restrictionCount; i++) {
            restrictions.add(readRestriction(in));
        }
        List<CommandNode<S>> nodes = new Builder<S>(records).build();
        return rebuild.view(viewHost, types, restrictions, nodes.get(0));
    }

    /** Every node reachable from {@code root} by children and redirects, {@code root} first. */
    private static <S> List<CommandNode<S>> number(CommandNode<S> root) {
        List<CommandNode<S>> nodes = new ArrayList<>();
        Map<CommandNode<S>, Boolean> seen = new IdentityHashMap<>();
        Deque<CommandNode<S>> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            CommandNode<S> node = pending.removeFirst();
            if (seen.put(node, Boolean.TRUE) != null) {
                continue;
            }
            nodes.add(node);
            pending.addAll(node.getChildren());
            if (node.getRedirect() != null) {
                pending.add(node.getRedirect());
            }
        }
        return nodes;
    }

    private <S> void writeNode(CommandNode<S> node, Map<CommandNode<S>, Integer> index, DataOutput out)
            throws IOException {
        out.writeByte(kind(node));
        out.writeUTF(node.getName());
        out.writeBoolean(node.getCommand() != null);
        Collection<CommandNode<S>> children = node.getChildren();
        out.writeInt(children.size());
        for (CommandNode<S> child : children) {
            out.writeInt(index.get(child));
        }
        out.writeInt(node.getRedirect() == null ? -1 : index.get(node.getRedirect()));
        if (node instanceof ArgumentCommandNode<S, ?> argument) {
            writeArgumentType(argument.getType(), out);
        }
    }

    private static byte kind(CommandNode<?> node) {
        if (node instanceof RootCommandNode<?>) {
            return ROOT;
        } else if (node instanceof LiteralCommandNode<?>) {
            return LITERAL;
        } else if (node instanceof ArgumentCommandNode<?, ?>) {
            return ARGUMENT;
        }
        throw new IllegalArgumentException("Cannot encode a " + node.getClass().getName()
                + " ('" + node.getName() + "'): only Brigadier's literal, argument and root nodes");
    }

    private void writeArgumentType(ArgumentType<?> type, DataOutput out) throws IOException {
        Optional<ArgumentDescription> description = arguments.describe(type);
        if (description.isEmpty()) {
            out.writeByte(HOST_ARGUMENT);
            host.writeArgumentType(out, type);
            return;
        }
        if (description.get() instanceof ArgumentDescription.ValueOrLiteral valueOrLiteral) {
            out.writeByte(VALUE_OR_LITERAL);
            writeValueOrLiteral(valueOrLiteral, out);
        } else if (description.get() instanceof ArgumentDescription.Overloaded overloaded) {
            out.writeByte(OVERLOADED);
            out.writeInt(overloaded.variants().size());
            for (ArgumentDescription.ValueOrLiteral variant : overloaded.variants()) {
                writeValueOrLiteral(variant, out);
            }
        } else {
            throw new IllegalStateException("Unknown argument description: " + description.get());
        }
    }

    private void writeValueOrLiteral(ArgumentDescription.ValueOrLiteral description, DataOutput out)
            throws IOException {
        out.writeUTF(description.target().toString());
        host.writeArgumentType(out, description.literal());
    }

    private void writeRestriction(Restriction restriction, DataOutput out) throws IOException {
        out.writeUTF(restriction.kind().name());
        out.writeUTF(restriction.name());
        out.writeBoolean(restriction.input() != null);
        if (restriction.input() != null) {
            out.writeUTF(restriction.input().toString());
        }
        out.writeInt(restriction.variant());
        host.writeApplicability(out, restriction.applicability());
    }

    /** One node as read, before the nodes it points at exist. */
    private record NodeRecord(byte kind, String name, boolean runs, int[] children, int redirect,
                              @Nullable ArgumentType<?> type) {
    }

    private NodeRecord readNode(DataInput in, int position, int count, ScriptArguments.Rebuild rebuild)
            throws IOException {
        byte kind = in.readByte();
        if (kind != LITERAL && kind != ARGUMENT && kind != ROOT) {
            throw new IOException("Node " + position + " has unknown kind " + kind);
        }
        if (kind == ROOT && position != 0) {
            throw new IOException("Node " + position + " is a root, but only the first node may be");
        }
        String name = in.readUTF();
        boolean runs = in.readBoolean();
        int childCount = readCount(in, "children of node " + position);
        List<Integer> read = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            read.add(readIndex(in, count, "a child of node " + position));
        }
        int[] children = read.stream().mapToInt(Integer::intValue).toArray();
        int redirect = in.readInt();
        if (redirect < -1 || redirect >= count) {
            throw new IOException("Node " + position + " redirects to " + redirect + ", but there are " + count
                    + " nodes");
        }
        ArgumentType<?> type = kind == ARGUMENT ? readArgumentType(in, rebuild) : null;
        return new NodeRecord(kind, name, runs, children, redirect, type);
    }

    private ArgumentType<?> readArgumentType(DataInput in, ScriptArguments.Rebuild rebuild) throws IOException {
        byte form = in.readByte();
        return switch (form) {
            case HOST_ARGUMENT -> host.readArgumentType(in);
            case VALUE_OR_LITERAL -> rebuild.argument(readValueOrLiteral(in));
            case OVERLOADED -> {
                int variantCount = readCount(in, "overload variants");
                List<ArgumentDescription.ValueOrLiteral> variants = new ArrayList<>();
                for (int i = 0; i < variantCount; i++) {
                    variants.add(readValueOrLiteral(in));
                }
                yield rebuild.argument(new ArgumentDescription.Overloaded(variants));
            }
            default -> throw new IOException("Unknown argument type form " + form);
        };
    }

    private ArgumentDescription.ValueOrLiteral readValueOrLiteral(DataInput in) throws IOException {
        TypeKey target = readKey(in);
        return new ArgumentDescription.ValueOrLiteral(host.readArgumentType(in), target);
    }

    private Restriction readRestriction(DataInput in) throws IOException {
        String kindName = in.readUTF();
        Restriction.Kind kind;
        try {
            kind = Restriction.Kind.valueOf(kindName);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown restriction kind '" + kindName + "'", e);
        }
        String name = in.readUTF();
        TypeKey input = in.readBoolean() ? readKey(in) : null;
        int variant = in.readInt();
        return new Restriction(kind, name, input, variant, host.readApplicability(in));
    }

    private static TypeKey readKey(DataInput in) throws IOException {
        String text = in.readUTF();
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IOException("'" + text + "' is not a type key");
        }
        return new TypeKey(text.substring(0, colon), text.substring(colon + 1));
    }

    private static int readCount(DataInput in, String what) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative count of " + what + ": " + count);
        }
        return count;
    }

    private static int readIndex(DataInput in, int count, String what) throws IOException {
        int index = in.readInt();
        if (index < 0 || index >= count) {
            throw new IOException(what + " is node " + index + ", but there are " + count + " nodes");
        }
        return index;
    }

    /** Makes the nodes, each after the node it redirects to, then joins them up in their original order. */
    private static final class Builder<S> {
        private final List<NodeRecord> records;
        private final List<@Nullable CommandNode<S>> made;
        private final boolean[] making;

        Builder(List<NodeRecord> records) {
            this.records = records;
            this.made = new ArrayList<>(Collections.nCopies(records.size(), null));
            this.making = new boolean[records.size()];
        }

        List<CommandNode<S>> build() throws IOException {
            for (int i = 0; i < records.size(); i++) {
                make(i);
            }
            for (int i = 0; i < records.size(); i++) {
                CommandNode<S> parent = made.get(i);
                for (int child : records.get(i).children()) {
                    if (child == 0 && records.get(0).kind() == ROOT) {
                        throw new IOException("Node " + i + " has the root as a child");
                    }
                    parent.addChild(made.get(child));
                }
            }
            List<CommandNode<S>> nodes = new ArrayList<>();
            made.forEach(nodes::add);
            return nodes;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CommandNode<S> make(int i) throws IOException {
            CommandNode<S> existing = made.get(i);
            if (existing != null) {
                return existing;
            }
            if (making[i]) {
                throw new IOException("Node " + i + " redirects to itself, through other nodes");
            }
            making[i] = true;
            NodeRecord record = records.get(i);
            CommandNode<S> redirect = record.redirect() < 0 ? null : make(record.redirect());
            Command<S> command = record.runs() ? cannotRun() : null;
            CommandNode<S> node = switch (record.kind()) {
                case ROOT -> {
                    if (redirect != null) {
                        throw new IOException("The root cannot redirect");
                    }
                    yield new RootCommandNode<>();
                }
                case LITERAL -> new LiteralCommandNode<>(record.name(), command, source -> true, redirect, null,
                        false);
                default -> new ArgumentCommandNode(record.name(), (ArgumentType) record.type(), command,
                        source -> true, redirect, null, false, null);
            };
            made.set(i, node);
            return node;
        }

        /** Marks a node that ran on the server: the client parses as if it could, but never runs it. */
        private static <S> Command<S> cannotRun() {
            return context -> {
                throw new IllegalStateException("A script tree decoded on a client only parses and suggests;"
                        + " it cannot run");
            };
        }
    }
}
