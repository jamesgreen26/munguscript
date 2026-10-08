package g_mungus.munguscript.engine_impl.expression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.munguscript.engine_impl.argument.CommandText;
import g_mungus.munguscript.engine_impl.argument.LiteralOfArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.argument.ValueOfException;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.engine_impl.tree.Conversions;
import g_mungus.munguscript.engine_impl.tree.TypeGraph;
import g_mungus.munguscript.engine_impl.tree.TypeNames;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Reads expressions (what a {@code value_of(...)} holds) against the tree, without running them:
 * for checking an expression before it runs, for explaining why it cannot be used, and for probes
 * and suggestions.
 *
 * <p>Expressions are parsed in place, from where they start in the command up to where they end,
 * so every position in a parse, including those of nested {@code value_of}s, is a position in the
 * command.
 */
public final class ExpressionReader<S> {
    private final TypeGraph graph;
    private final Conversions conversions;
    private final TypeNames names;
    private final RootCommandNode<S> root = new RootCommandNode<>();
    private final CommandDispatcher<S> dispatcher = new CommandDispatcher<>(root);

    public ExpressionReader(ScriptTree<S> tree, TypeGraph graph, Conversions conversions, TypeNames names) {
        this.graph = graph;
        this.conversions = conversions;
        this.names = names;
        // Brigadier only parses from a dispatcher's root, so the getters are put under one of its own.
        tree.value().getChildren().forEach(root::addChild);
    }

    /** Which types each type is usable as, and how a value becomes one. */
    public Conversions conversions() {
        return conversions;
    }

    /** The dispatcher expressions are parsed and run with. Its root stands for the tree's value node. */
    public CommandDispatcher<S> dispatcher() {
        return dispatcher;
    }

    /** Parses what is in {@code input} from {@code start} to {@code end}. */
    public ParseResults<S> parse(String input, int start, int end, @Nullable S source) {
        StringReader reader = new StringReader(input.substring(0, end));
        reader.setCursor(CommandText.skipSpaces(reader.getString(), start));
        return dispatcher.parse(reader, source);
    }

    /** How far {@code valueOf}'s expression reads, and what it gives. */
    public Shape shape(ValueOf valueOf, @Nullable S source) {
        ParseResults<S> parse = parse(valueOf.input(), valueOf.innerStart(), valueOf.innerEnd(), source);
        ImmutableStringReader reader = parse.getReader();
        ParsedCommandNode<S> last = lastNode(parse.getContext());
        if (last == null) {
            if (!reader.canRead()) {
                return new Shape.Empty();
            }
            String problem = LiteralOfArgument.problem(reader.getString(), reader.getCursor());
            return problem != null ? new Shape.BadLiteral(problem) : new Shape.UnknownStart(wordAt(reader));
        }
        Optional<TypeKey> type = ScriptTree.typeAfter(last.getNode());
        if (reader.canRead()) {
            // Parsing stopped either in a mapper's argument, or at a word that is no mapper for the
            // value so far: literals that do not match are not even tried, so they leave no error.
            Optional<ValueOfException> nested = parse.getExceptions().values().stream()
                    .filter(ValueOfException.class::isInstance).map(ValueOfException.class::cast).findFirst();
            if (nested.isPresent()) {
                return new Shape.Nested(nested.get());
            }
            return type.isEmpty() || !parse.getExceptions().isEmpty()
                    ? new Shape.BadArgument(wordAt(reader), last.getNode().getName())
                    : new Shape.CannotFollow(wordAt(reader), type.get());
        }
        return type.<Shape>map(key -> new Shape.Gives<>(key, parse))
                .orElseGet(() -> new Shape.MissingArgument(last.getNode().getName()));
    }

    /** Reads {@code valueOf} where {@code owner} wants a {@code target}. */
    public Result<S> read(ValueOf valueOf, String owner, TypeKey target, @Nullable S source) {
        return read(valueOf, owner, List.of(target), source);
    }

    /**
     * Reads {@code valueOf} where {@code owner} takes any of {@code targets}. A {@code value_of}
     * nested in it has already been checked by its own argument slot while this one was parsed. It
     * is read as the target it gives, or else the nearest one it is usable as.
     *
     * @param targets in order of preference; a failure is explained against the first
     */
    public Result<S> read(ValueOf valueOf, String owner, List<TypeKey> targets, @Nullable S source) {
        Shape shape = shape(valueOf, source);
        if (shape instanceof Shape.Nested nested) {
            return new Result.Unreadable<>(nested.problem().reason(), nested.problem().range());
        }
        if (!(shape instanceof Shape.Gives<?> gives)) {
            return unreadable(valueOf, explain(shape, valueOf, owner, targets.get(0)));
        }
        Optional<TypeKey> as = conversions.best(gives.type(), targets);
        if (as.isEmpty()) {
            return unreadable(valueOf, wrongType(valueOf, gives.type(), owner, targets.get(0)));
        }
        @SuppressWarnings("unchecked")
        ParseResults<S> parse = (ParseResults<S>) gives.parse();
        return new Result.Readable<>(parse, gives.type(), as.get());
    }

    /** Whether {@code expression} reads, in full, as a {@code type}. */
    public boolean readsAs(String expression, TypeKey type, @Nullable S source) {
        return read(ValueOf.whole(expression), "the expression", type, source) instanceof Result.Readable<S>;
    }

    /** What {@code expression} gives, if it reads in full. */
    public Optional<TypeKey> typeOf(String expression, @Nullable S source) {
        return shape(ValueOf.whole(expression), source) instanceof Shape.Gives<?> gives
                ? Optional.of(gives.type()) : Optional.empty();
    }

    private String explain(Shape shape, ValueOf valueOf, String owner, TypeKey target) {
        String text = valueOf.expression();
        if (shape instanceof Shape.Empty) {
            return "value_of() is empty, and " + owner + " needs " + names.of(target);
        } else if (shape instanceof Shape.UnknownStart start) {
            return "'" + start.word() + "' is not a known value in value_of(" + text + ")";
        } else if (shape instanceof Shape.BadLiteral literal) {
            return "In value_of(" + text + "), " + literal.reason();
        } else if (shape instanceof Shape.MissingArgument missing) {
            return "value_of(" + text + ") is incomplete: '" + missing.mapper() + "' needs a value after it";
        } else if (shape instanceof Shape.BadArgument bad) {
            return "In value_of(" + text + "), '" + bad.word() + "' is not a valid value for '"
                    + bad.mapper() + "'";
        } else if (shape instanceof Shape.CannotFollow follow) {
            return "In value_of(" + text + "), '" + follow.word() + "' cannot follow " + names.of(follow.type());
        } else if (shape instanceof Shape.Gives<?> gives) {
            return wrongType(valueOf, gives.type(), owner, target);
        } else if (shape instanceof Shape.Nested nested) {
            return nested.problem().reason();
        }
        throw new IllegalStateException("Unknown shape: " + shape);
    }

    private String wrongType(ValueOf valueOf, TypeKey given, String owner, TypeKey target) {
        String gives = "value_of(" + valueOf.expression() + ") gives " + names.of(given);
        return graph.reaches(given, target)
                ? gives + ", but " + owner + " needs " + names.of(target)
                : gives + ", which cannot be turned into " + names.of(target);
    }

    private Result<S> unreadable(ValueOf valueOf, String reason) {
        return new Result.Unreadable<>(reason, valueOf.range());
    }

    /** The last node parsed, across every redirect. */
    public static <S> @Nullable ParsedCommandNode<S> lastNode(CommandContextBuilder<S> context) {
        ParsedCommandNode<S> last = null;
        for (CommandContextBuilder<S> current = context; current != null; current = current.getChild()) {
            if (!current.getNodes().isEmpty()) {
                List<ParsedCommandNode<S>> nodes = current.getNodes();
                last = nodes.get(nodes.size() - 1);
            }
        }
        return last;
    }

    private static String wordAt(ImmutableStringReader reader) {
        String text = reader.getString();
        return text.substring(reader.getCursor(), CommandText.wordEnd(text, reader.getCursor()));
    }

    /** Whether {@code node} is where expressions start, standing for the tree's value node. */
    public boolean isRoot(CommandNode<S> node) {
        return node == root;
    }

    /** An expression that can be used where it stands, or why not. */
    public sealed interface Result<S> {

        /** It gives a {@code type}, which is used as an {@code as}: the same type, or one it is usable as. */
        record Readable<S>(ParseResults<S> parse, TypeKey type, TypeKey as) implements Result<S> {
        }

        /** Why not, worded for the script's author, and the {@code value_of(...)} at fault. */
        record Unreadable<S>(String reason, StringRange range) implements Result<S> {
        }
    }
}
