package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.CompiledCommand;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.LivePolicy;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.ExpressionProbe;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.build.Registrations;
import g_mungus.munguscript.engine_impl.build.Registry;
import g_mungus.munguscript.engine_impl.build.TreeBuilder;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.failure.FailureDescriber;
import g_mungus.munguscript.engine_impl.run.CommandRun;
import g_mungus.munguscript.engine_impl.run.Evaluator;
import g_mungus.munguscript.engine_impl.run.NodeActions;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One build: the registrations, the tree built from them, and a view over that tree. Everything a
 * view does is delegated to it; what only a server can do (running, evaluating, describing a
 * failure) is here.
 */
final class ScriptEngineImpl<S> implements ScriptEngine<S> {
    /** How errors name what wants the value of {@link #evaluate}. */
    private static final String EVALUATE_OWNER = "the command";

    private final ScriptHost<S> host;
    private final Registrations registrations;
    private final ScriptTree<S> tree;
    private final ViewImpl<S> view;
    private final Evaluator<S> evaluator;
    private final FailureDescriber<S> failures;

    ScriptEngineImpl(ScriptHost<S> host, BuildEnvironment environment, Consumer<ScriptRegistrar> registrations) {
        this.host = host;
        Registry registry = new Registry();
        registrations.accept(registry);
        this.registrations = registry.finish();
        // The tree's nodes evaluate value_of through the view's reader, and the view reads the tree:
        // the evaluator reaches the view lazily, and the argument types once it exists.
        this.evaluator = new Evaluator<>(host, this::expressions);
        ArgumentLookup lookup = new ArgumentLookup();
        this.tree = new TreeBuilder<>(this.registrations, environment, new NodeActions<>(host, evaluator), lookup).build();
        this.view = new ViewImpl<>(host, this.registrations.types(), this.registrations.restrictions(), tree);
        lookup.pointAt(view.arguments());
        this.failures = new FailureDescriber<>(view.commands());
    }

    private ExpressionReader<S> expressions() {
        return view.expressions();
    }

    @Override
    public Collection<ScriptType<?>> types() {
        return registrations.types();
    }

    @Override
    public Collection<ScriptNode> nodes() {
        return registrations.nodes();
    }

    @Override
    public void graft(CommandNode<S> parent) {
        tree.roots().forEach(parent::addChild);
    }

    @Override
    public List<Restriction> restrictions() {
        return registrations.restrictions();
    }

    @Override
    public CommandNode<S> scriptRoot() {
        return view.scriptRoot();
    }

    @Override
    public S begin(S source) {
        return host.withRunState(source, new CommandRun());
    }

    @Override
    public <T> T evaluate(String expression, ScriptType<T> type, S source) {
        return type.javaClass().cast(evaluator.evaluate(ValueOf.whole(expression), type.key(), EVALUATE_OWNER, source));
    }

    @Override
    public LiveLine<S> compileLive(String command, S source, LivePolicy policy) throws CommandSyntaxException {
        ParseResults<S> parse = view.parse(command, source);
        refuseIncomplete(parse);
        Set<ScriptNode> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(registrations.standards());
        for (ScriptNode node : registrations.nodes()) {
            if (policy.live(node)) {
                live.add(node);
            }
        }
        return new LiveLineImpl<>(host, view.commands(), parse, live);
    }

    @Override
    public CompiledCommand<S> compile(String command, S source) throws CommandSyntaxException {
        ParseResults<S> parse = view.parse(command, source);
        refuseIncomplete(parse);
        return new CompiledCommandImpl<>(host, view.commands(), parse);
    }

    /** Refuses a parse that did not read the whole command to a node that runs, as Brigadier's execute would. */
    private static void refuseIncomplete(ParseResults<?> parse) throws CommandSyntaxException {
        if (parse.getReader().canRead()) {
            if (parse.getExceptions().size() == 1) {
                throw parse.getExceptions().values().iterator().next();
            } else if (parse.getContext().getRange().isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
            }
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader());
        }
        CommandContextBuilder<?> last = parse.getContext();
        while (last.getChild() != null) {
            last = last.getChild();
        }
        if (last.getCommand() == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
        }
    }

    @Override
    public ScriptFailure describe(Throwable failure, String playerCommand, String executedCommand, SourceMap sourceMap) {
        return failures.describe(failure, playerCommand, executedCommand, sourceMap);
    }

    @Override
    public Optional<ScriptType<?>> type(TypeKey key) {
        return view.type(key);
    }

    @Override
    public String typeName(TypeKey key) {
        return view.typeName(key);
    }

    @Override
    public ParseResults<S> parse(String command, S source) {
        return view.parse(command, source);
    }

    @Override
    public ParseResults<S> parseAt(String command, int cursor, S source) {
        return view.parseAt(command, cursor, source);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(String command, int cursor, S source,
                                                  CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.suggest(command, cursor, source, preProcessing);
    }

    @Override
    public List<Highlight> highlight(String command, S source, CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.highlight(command, source, preProcessing);
    }

    @Override
    public CompletableFuture<Suggestions> suggestExpression(String expression, int cursor, S source,
                                                            @Nullable TypeKey type,
                                                            CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.suggestExpression(expression, cursor, source, type, preProcessing);
    }

    @Override
    public List<Highlight> highlightExpression(String expression, S source,
                                               CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.highlightExpression(expression, source, preProcessing);
    }

    @Override
    public List<Highlight> highlightDefinition(String body, S source,
                                               CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.highlightDefinition(body, source, preProcessing);
    }

    @Override
    public Optional<ScriptFailure> check(String command, S source,
                                         CommandPreProcessor.@Nullable Prepared preProcessing) {
        return view.check(command, source, preProcessing);
    }

    @Override
    public Vocabulary vocabulary() {
        return view.vocabulary();
    }

    @Override
    public ExpressionProbe probe(S source) {
        return view.probe(source);
    }

    @Override
    public CommandPreProcessor aliases() {
        return view.aliases();
    }
}
