package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.ExpressionProbe;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.Rewriter;
import g_mungus.munguscript.engine.preprocess.Rewritten;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.CommandText;
import g_mungus.munguscript.engine_impl.argument.LiteralOfArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.highlight.Highlighter;
import g_mungus.munguscript.engine_impl.preprocess.AliasPreProcessor;
import g_mungus.munguscript.engine_impl.suggest.RestrictionIndex;
import g_mungus.munguscript.engine_impl.suggest.Suggester;
import g_mungus.munguscript.engine_impl.suggest.Tokens;
import g_mungus.munguscript.engine_impl.tree.Conversions;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.engine_impl.tree.TypeGraph;
import g_mungus.munguscript.engine_impl.tree.TypeNames;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Parsing, suggesting and probing over a script tree, without running anything. An engine is a
 * view over the tree it built; a client's view is over a tree it received, found again by name.
 * Both read the language from the tree alone, so they behave the same.
 */
final class ViewImpl<S> implements ScriptView<S> {
    private final ScriptViewHost<S> host;
    private final Map<TypeKey, ScriptType<?>> types = new LinkedHashMap<>();
    private final TypeNames names;
    private final ScriptTree<S> tree;
    private final CommandDispatcher<S> commands;
    private final ExpressionReader<S> expressions;
    private final Suggester<S> suggester;
    private final ViewArguments<S> arguments;
    private final Highlighter<S> highlighter;

    /**
     * @param types the types scripts may use; the built-in ones are always known
     */
    ViewImpl(ScriptViewHost<S> host, Collection<ScriptType<?>> types, Collection<Restriction> restrictions,
             ScriptTree<S> tree) {
        this.host = host;
        BuiltInTypes.ALL.forEach(type -> this.types.put(type.key(), type));
        types.forEach(type -> this.types.put(type.key(), type));
        this.names = new TypeNames(host.defaultNamespace());
        this.tree = tree;
        // Brigadier only parses from a dispatcher's root, so the script root's children are put under one.
        RootCommandNode<S> root = new RootCommandNode<>();
        tree.script().getChildren().forEach(root::addChild);
        this.commands = new CommandDispatcher<>(root);
        Conversions conversions = Conversions.of(this.types.values());
        TypeGraph graph = TypeGraph.of(tree).with(conversions.edges());
        this.expressions = new ExpressionReader<>(tree, graph, conversions, names);
        this.suggester = new Suggester<>(tree, graph, TypeGraph.withoutConversions(tree), expressions, commands,
                new RestrictionIndex<>(host, restrictions));
        this.arguments = new ViewArguments<>(tree, suggester, expressions);
        this.highlighter = new Highlighter<>(tree, commands, expressions);
    }

    /** A view over a tree built elsewhere, found under the node it was grafted under. */
    static <S> ViewImpl<S> over(ScriptViewHost<S> host, Collection<ScriptType<?>> types,
                                Collection<Restriction> restrictions, CommandNode<S> graftedUnder) {
        return new ViewImpl<>(host, types, restrictions, ScriptTree.find(graftedUnder));
    }

    @Override
    public Optional<ScriptType<?>> type(TypeKey key) {
        return Optional.ofNullable(types.get(key));
    }

    @Override
    public String typeName(TypeKey key) {
        return names.of(key);
    }

    @Override
    public CommandNode<S> scriptRoot() {
        return tree.script();
    }

    @Override
    public ParseResults<S> parse(String command, S source) {
        return commands.parse(command, source);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(String command, int cursor, S source,
                                                  CommandPreProcessor.@Nullable Prepared preProcessing) {
        String typed = typedBefore(command, cursor);
        Rewritten text = preProcessing == null ? Rewritten.unchanged(typed) : preProcess(typed, preProcessing, source);
        Tokens tokens = preProcessing == null ? Tokens.NONE : new Tokens(preProcessing.tokens());
        List<Suggestion> suggestions = new ArrayList<>();
        for (Suggestion suggestion : suggester.inCommand(text.text(), source, tokens)) {
            suggestions.add(new Suggestion(text.map().toOriginal(suggestion.getRange()), suggestion.getText(),
                    suggestion.getTooltip()));
        }
        return CompletableFuture.completedFuture(Suggester.collect(command, suggestions));
    }

    @Override
    public CompletableFuture<Suggestions> suggestExpression(String expression, int cursor, S source,
                                                            @Nullable TypeKey type,
                                                            CommandPreProcessor.@Nullable Prepared preProcessing) {
        // Pre-processors rewrite commands, where an expression starts inside a value_of(, so the
        // expression is suggested for inside one, with nothing to close.
        int offset = ValueOf.OPEN.length();
        String typed = ValueOf.OPEN + typedBefore(expression, cursor);
        Rewritten text = preProcessing == null ? Rewritten.unchanged(typed) : preProcess(typed, preProcessing, source);
        Tokens tokens = preProcessing == null ? Tokens.NONE : new Tokens(preProcessing.tokens());
        List<Suggestion> suggestions = new ArrayList<>();
        for (Suggestion suggestion : suggester.inLoneExpression(text.text(), offset,
                type == null ? null : Set.of(type), source, tokens)) {
            StringRange range = text.map().toOriginal(suggestion.getRange());
            suggestions.add(new Suggestion(StringRange.between(Math.max(range.getStart() - offset, 0),
                    Math.max(range.getEnd() - offset, 0)), suggestion.getText(), suggestion.getTooltip()));
        }
        return CompletableFuture.completedFuture(Suggester.collect(expression, suggestions));
    }

    /** What is written before {@code cursor}, which is kept within the text: before it is nothing, past it is all. */
    private static String typedBefore(String text, int cursor) {
        return text.substring(0, Math.max(0, Math.min(cursor, text.length())));
    }

    @Override
    public List<Highlight> highlight(String command, S source, CommandPreProcessor.@Nullable Prepared preProcessing) {
        if (preProcessing == null) {
            return highlighter.highlight(command, command, SourceMap.IDENTITY, List.of(), source);
        }
        PreProcessed processed = preProcessing.process(command,
                new PreProcessContext(probe(source), host.hostContext(source)));
        return highlighter.highlight(command, processed.command(), processed.sourceMap(), preProcessing.tokens(),
                source);
    }

    @Override
    public List<Highlight> highlightExpression(String expression, S source,
                                               CommandPreProcessor.@Nullable Prepared preProcessing) {
        if (preProcessing == null) {
            return highlighter.highlightExpression(expression, expression, 0, expression.length(), SourceMap.IDENTITY,
                    List.of(), 0, expression.length(), source);
        }
        // Pre-processors rewrite commands, where an expression starts inside a value_of(...).
        String wrapped = ValueOf.OPEN + expression + ")";
        PreProcessed processed = preProcessing.process(wrapped,
                new PreProcessContext(probe(source), host.hostContext(source)));
        String text = processed.command();
        return highlighter.highlightExpression(wrapped, text, ValueOf.OPEN.length(), text.length() - 1,
                processed.sourceMap(), preProcessing.tokens(), ValueOf.OPEN.length(), expression.length(), source);
    }

    @Override
    public List<Highlight> highlightDefinition(String body, S source,
                                               CommandPreProcessor.@Nullable Prepared preProcessing) {
        int start = CommandText.skipSpaces(body, 0);
        int end = body.stripTrailing().length();
        String text = body.substring(start, Math.max(start, end));
        boolean named = tree.isGetter(text) || preProcessing != null
                && preProcessing.tokens().stream().anyMatch(token -> token.text().equals(text));
        if (!text.isEmpty() && !named && LiteralOfArgument.literalType(text).isPresent()) {
            return List.of(new Highlight(StringRange.between(start, end), Highlight.Kind.ARGUMENT));
        }
        return highlightExpression(body, source, preProcessing);
    }

    /**
     * Pre-processes what has been typed, except the word being typed: an alias that has only been
     * half written, or that the cursor is still on, should be suggested, not expanded. The result
     * maps back to what was typed.
     */
    private Rewritten preProcess(String typed, CommandPreProcessor.Prepared preProcessing, S source) {
        int word = typed.lastIndexOf(' ') + 1;
        PreProcessContext context = new PreProcessContext(probe(source), host.hostContext(source));
        PreProcessed head = preProcessing.process(typed.substring(0, word), context);
        return new Rewriter(typed)
                .rewrite(0, word, new Rewritten(head.command(), head.sourceMap()))
                .keep(word, typed.length())
                .build();
    }

    @Override
    public ExpressionProbe probe(S source) {
        return (expression, type) -> expressions.readsAs(expression, type, source);
    }

    @Override
    public CommandPreProcessor aliases() {
        return new AliasPreProcessor<>(tree, expressions);
    }

    ExpressionReader<S> expressions() {
        return expressions;
    }

    CommandDispatcher<S> commands() {
        return commands;
    }

    /** What this view does for the engine's argument types in its tree. */
    ArgumentLookup.ArgumentView arguments() {
        return arguments;
    }
}
