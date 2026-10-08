package g_mungus.munguscript.engine_impl.suggest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.OverloadedArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.tree.NodeNames;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.engine_impl.tree.TypeGraph;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * What to suggest at the end of a command being written. Brigadier finds the node the cursor is
 * after; the suggester then offers that node's children, leaving out what is not meant for the
 * target or cannot lead to the type wanted, and reaches into {@code value_of(...)} by reading the
 * expression in place, as many levels deep as it is open.
 *
 * <p>Until a word has been started, only what works without converting a value is offered: a word a
 * chain holds only through a conversion, or one that only leads to what is wanted through one, is
 * left out. Once it has been started, it is offered like any other.
 *
 * <p>The engine's argument types suggest through this too ({@link ArgumentLookup}), so a client's
 * own dispatcher gets the same suggestions inside {@code value_of} as the view does.
 */
public final class Suggester<S> {
    private static final String CLOSE = ")";

    private final ScriptTree<S> tree;
    private final TypeGraph graph;
    private final TypeGraph withoutConversions;
    private final ExpressionReader<S> expressions;
    private final CommandDispatcher<S> commands;
    private final RestrictionIndex<S> restrictions;

    /**
     * @param graph              what each type can be turned into, conversions included
     * @param withoutConversions what each type can be turned into without converting a value
     */
    public Suggester(ScriptTree<S> tree, TypeGraph graph, TypeGraph withoutConversions,
                     ExpressionReader<S> expressions, CommandDispatcher<S> commands,
                     RestrictionIndex<S> restrictions) {
        this.tree = tree;
        this.graph = graph;
        this.withoutConversions = withoutConversions;
        this.expressions = expressions;
        this.commands = commands;
        this.restrictions = restrictions;
    }

    /** Suggestions at the end of {@code text}, a script command. Ranges are in {@code text}. */
    public List<Suggestion> inCommand(String text, S source, Tokens tokens) {
        return atEnd(commands.parse(text, source), text, null, source, tokens);
    }

    /** Suggestions for one of the engine's argument types, as Brigadier asks a type for them. */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Suggestions> forArgument(ArgumentType<?> argument, CommandContext<T> context,
                                                      SuggestionsBuilder builder) {
        // Brigadier hands an argument type the context of the whole command; the node the argument
        // belongs to is the last one parsed.
        List<ParsedCommandNode<T>> nodes = context.getLastChild().getNodes();
        String owner = nodes.isEmpty() ? "" : nodes.get(nodes.size() - 1).getNode().getName();
        CommandContext<S> own = (CommandContext<S>) context;
        return CompletableFuture.completedFuture(collect(builder.getInput(),
                argument(argument, owner, own, builder, own.getSource(), Tokens.NONE)));
    }

    /**
     * Gathers suggestions as they are. Brigadier's own merging widens every suggestion to a common
     * range, which would turn a {@code )} after a word into the word and the parenthesis.
     */
    public static Suggestions collect(String input, Collection<Suggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return new Suggestions(StringRange.at(input.length()), List.of());
        }
        int start = suggestions.stream().mapToInt(suggestion -> suggestion.getRange().getStart()).min().orElseThrow();
        int end = suggestions.stream().mapToInt(suggestion -> suggestion.getRange().getEnd()).max().orElseThrow();
        List<Suggestion> unique = new ArrayList<>(new LinkedHashSet<>(suggestions));
        unique.sort((a, b) -> a.getText().compareToIgnoreCase(b.getText()));
        return new Suggestions(StringRange.between(start, end), unique);
    }

    /**
     * What may come at the end of a parse: the children of the node the cursor is after.
     *
     * @param targets the types wanted, inside a {@code value_of}; null in a command
     */
    private List<Suggestion> atEnd(ParseResults<S> parse, String text, @Nullable Set<TypeKey> targets, S source,
                                   Tokens tokens) {
        SuggestionContext<S> at = parse.getContext().findSuggestionContext(text.length());
        CommandContext<S> context = parse.getContext().build(text);
        StringRange range = StringRange.between(at.startPos, text.length());
        String typed = text.substring(at.startPos);
        List<Suggestion> suggestions = new ArrayList<>();
        for (CommandNode<S> child : at.parent.getChildren()) {
            if (child instanceof LiteralCommandNode<S> literal) {
                if (literal.getLiteral().toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))
                        && offered(at.parent, literal, targets, typed, source)) {
                    suggestions.add(new Suggestion(range, literal.getLiteral()));
                }
            } else if (child instanceof ArgumentCommandNode<S, ?> argument) {
                SuggestionsBuilder builder = new SuggestionsBuilder(text, at.startPos);
                suggestions.addAll(argument(argument, at.parent.getName(), context, builder, source, tokens));
            }
        }
        expressionTargets(at.parent, targets).ifPresent(wanted ->
                suggestions.addAll(tokens.expressions(range, typed, type -> reach(typed).reachesAny(type, wanted))));
        return suggestions;
    }

    /** Suggestions for an argument slot: the engine's own, or whatever a host's argument type offers. */
    private List<Suggestion> argument(ArgumentCommandNode<S, ?> node, String owner, CommandContext<S> context,
                                      SuggestionsBuilder builder, S source, Tokens tokens) {
        if (node.getType() instanceof ValueOrLiteralArgument || node.getType() instanceof OverloadedArgument) {
            return argument(node.getType(), owner, context, builder, source, tokens);
        }
        try {
            return node.listSuggestions(context, builder).join().getList();
        } catch (CommandSyntaxException e) {
            return List.of();
        }
    }

    private List<Suggestion> argument(ArgumentType<?> type, String owner, CommandContext<S> context,
                                      SuggestionsBuilder builder, S source, Tokens tokens) {
        List<ValueOrLiteralArgument> slots = slots(type, owner, source);
        Set<TypeKey> targets = new LinkedHashSet<>();
        slots.forEach(slot -> targets.add(slot.target()));
        if (ValueOf.startsAt(builder.getInput(), builder.getStart())) {
            return inExpression(builder.getInput(), builder.getStart() + ValueOf.OPEN.length(), targets, source, tokens);
        }
        List<Suggestion> suggestions = new ArrayList<>(ValueOrLiteralArgument.written(slots, context, builder));
        suggestions.addAll(tokens.arguments(StringRange.between(builder.getStart(), builder.getInput().length()),
                builder.getRemaining(), targets));
        ValueOrLiteralArgument.opening(builder, suggestions.isEmpty()).ifPresent(suggestions::add);
        return suggestions;
    }

    /** The slots an argument offers: for executors that share a name, those meant for the target. */
    private List<ValueOrLiteralArgument> slots(ArgumentType<?> type, String owner, S source) {
        if (type instanceof OverloadedArgument overloaded) {
            List<ValueOrLiteralArgument> applicable = new ArrayList<>();
            for (int i = 0; i < overloaded.variants().size(); i++) {
                if (restrictions.variantApplies(owner, i, source)) {
                    applicable.add(overloaded.variants().get(i));
                }
            }
            return applicable;
        }
        return List.of((ValueOrLiteralArgument) type);
    }

    /** Inside an open {@code value_of(}, whose expression runs from {@code start} to the end of {@code input}. */
    private List<Suggestion> inExpression(String input, int start, Set<TypeKey> targets, S source, Tokens tokens) {
        ParseResults<S> parse = expressions.parse(input, start, input.length(), source);
        List<Suggestion> suggestions = atEnd(parse, input, targets, source, tokens);
        ParsedCommandNode<S> last = ExpressionReader.lastNode(parse.getContext());
        if (!parse.getReader().canRead() && last != null
                && ScriptTree.typeAfter(last.getNode()).filter(targets::contains).isPresent()) {
            suggestions.add(new Suggestion(StringRange.at(input.length()), CLOSE));
        }
        return suggestions;
    }

    /**
     * Whether a literal under {@code parent} is offered: meant for the target, and able to lead to
     * what is wanted, without a conversion unless {@code typed} has started the word.
     */
    private boolean offered(CommandNode<S> parent, LiteralCommandNode<S> child, @Nullable Set<TypeKey> targets,
                            String typed, S source) {
        String name = child.getLiteral();
        if (ScriptTree.isKeyword(name)) {
            return true;
        }
        if (ScriptTree.isExecutor(child)) {
            return restrictions.executorApplies(name, variantCount(child), source);
        }
        if (typed.isEmpty() && tree.isConverted(child)) {
            return false;
        }
        // In a condition, what is wanted is a boolean. Every chain the tree has there can reach one,
        // but perhaps only through a conversion.
        Set<TypeKey> wanted = targets != null || !inCondition(parent) ? targets : Set.of(BuiltInTypes.BOOLEAN.key());
        boolean leadsOn = wanted == null || ScriptTree.outputOf(child)
                .filter(type -> reach(typed).reachesAny(type, wanted)).isPresent();
        if (isValueRoot(parent) || parent == tree.condition()) {
            return leadsOn && restrictions.getterApplies(name, source);
        }
        Optional<TypeKey> input = NodeNames.chainType(parent.getName());
        return input.isEmpty() || leadsOn && restrictions.mapperApplies(name, input.get(), source);
    }

    /** What a type can be turned into, for offering words: without conversions until one is started. */
    private TypeGraph reach(String typed) {
        return typed.isEmpty() ? withoutConversions : graph;
    }

    private boolean inCondition(CommandNode<S> parent) {
        return parent == tree.condition() || NodeNames.isConditionChain(parent.getName());
    }

    /** At the start of an expression, the types it should lead to: a condition's boolean, or what a value_of wants. */
    private Optional<Set<TypeKey>> expressionTargets(CommandNode<S> parent, @Nullable Set<TypeKey> targets) {
        if (parent == tree.condition()) {
            return Optional.of(Set.of(BuiltInTypes.BOOLEAN.key()));
        }
        return isValueRoot(parent) && targets != null ? Optional.of(targets) : Optional.empty();
    }

    private boolean isValueRoot(CommandNode<S> node) {
        return node == tree.value() || expressions.isRoot(node);
    }

    private static int variantCount(CommandNode<?> executor) {
        return ScriptTree.argumentOf(executor)
                .map(argument -> argument.getType() instanceof OverloadedArgument overloaded ? overloaded.variants().size() : 1)
                .orElse(1);
    }
}
