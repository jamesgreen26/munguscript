package g_mungus.munguscript.runner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.MungusScript;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs whole scripts in one place: pre-processes each line, runs it, and stops at the first line
 * with a problem, which it locates in the script. For a host that runs scripts where it writes
 * them, such as a tool, a test or a server with no channel between the two.
 *
 * <p>A runner is one build of the engine, hosted in a Brigadier dispatcher of its own, so the host
 * needs no dispatcher or graft. To rebuild, build another runner.
 *
 * <pre>{@code
 * ScriptRunner<SimpleSource> runner = ScriptRunner.builder("my_app")
 *         .register(MyNodes::register)
 *         .build();
 * ScriptResult result = runner.run(script, new SimpleSource(myContext));
 * }</pre>
 *
 * @param <S> the host's command source
 */
public final class ScriptRunner<S> {
    /** The hidden literal the engine is grafted under, and the one a run is entered through. */
    private static final String GRAFT = "script_engine";
    private static final String RUN = "run";

    private final ScriptHost<S> host;
    private final ScriptEngine<S> engine;
    private final CommandPreProcessor preProcessors;
    private final CommandDispatcher<S> dispatcher = new CommandDispatcher<>();
    private final CommandNode<S> grafted;

    private ScriptRunner(Builder<S> builder) {
        this.host = builder.host;
        this.engine = MungusScript.engine(builder.host, builder.environment,
                registrar -> builder.registrations.forEach(registrations -> registrations.accept(registrar)));
        this.preProcessors = CommandPreProcessor.chain(List.copyOf(builder.preProcessors.apply(engine)));
        grafted = LiteralArgumentBuilder.<S>literal(GRAFT).build();
        dispatcher.getRoot().addChild(grafted);
        engine.graft(grafted);
        dispatcher.register(LiteralArgumentBuilder.<S>literal(RUN)
                .forward(engine.scriptRoot(), context -> List.of(engine.begin(context.getSource())), false));
    }

    /** A runner for {@code host}, which must carry run state on its sources. */
    public static <S> Builder<S> builder(ScriptHost<S> host) {
        return new Builder<>(host);
    }

    /** A runner for a {@link SimpleHost}: scripts run against a {@link SimpleSource}, with no targets. */
    public static Builder<SimpleSource> builder(String defaultNamespace) {
        return new Builder<>(new SimpleHost(defaultNamespace));
    }

    /** The engine this runner built, for parsing, suggesting and probing. */
    public ScriptEngine<S> engine() {
        return engine;
    }

    /**
     * Writes this runner's language as a {@link ScriptLanguageFile}, for tools that read scripts
     * without the host's code, such as an editor.
     */
    public void writeLanguage(DataOutput out) throws IOException {
        new ScriptLanguageFile().write(engine, grafted, host.defaultNamespace(), out);
    }

    /** Runs a script given as text, one command per line. */
    public ScriptResult run(String script, S source) {
        return run(script.lines().toList(), source);
    }

    /**
     * Runs a script's lines in order, skipping blank ones and those pre-processing used up, such
     * as alias definitions. Nothing runs if preparing the script found problems, and the script
     * stops at the first line that cannot be pre-processed or fails.
     */
    public ScriptResult run(List<String> lines, S source) {
        PreProcessContext context = new PreProcessContext(engine.probe(source), host.hostContext(source));
        CommandPreProcessor.Prepared prepared = preProcessors.prepare(lines, context);
        if (!prepared.diagnostics().isEmpty()) {
            return new ScriptResult(List.of(), prepared.diagnostics().stream()
                    .map(diagnostic -> problem(diagnostic, diagnostic.line(), lineAt(lines, diagnostic.line())))
                    .toList());
        }

        List<CommandResult> ran = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (prepared.consumedLines().contains(i) || lines.get(i).isBlank()) {
                continue;
            }
            String command = lines.get(i).strip();
            PreProcessed processed = prepared.process(command, context);
            if (!processed.diagnostics().isEmpty()) {
                int line = i;
                return new ScriptResult(ran, processed.diagnostics().stream()
                        .map(diagnostic -> problem(diagnostic, line, command))
                        .toList());
            }
            try {
                int result = dispatcher.execute(RUN + " " + processed.command(), source);
                ran.add(new CommandResult(i, command, processed.command(), result));
            } catch (Exception e) {
                ScriptFailure failure = engine.describe(e, command, processed.command(), processed.sourceMap());
                return new ScriptResult(ran, List.of(
                        new ScriptProblem(failure.reason(), i, command, failure.faultRange(), failure)));
            }
        }
        return new ScriptResult(ran, List.of());
    }

    /**
     * Evaluates what a script would write as {@code value_of(expression)} where {@code type} is
     * wanted, such as a formula in the host's configuration. Throws whatever the expression does.
     */
    public <T> T evaluate(String expression, ScriptType<T> type, S source) {
        return engine.evaluate(expression, type, engine.begin(source));
    }

    private static ScriptProblem problem(PreProcessDiagnostic diagnostic, @Nullable Integer line, @Nullable String text) {
        return new ScriptProblem(diagnostic.message(), line, text, diagnostic.range(), null);
    }

    private static @Nullable String lineAt(List<String> lines, @Nullable Integer index) {
        return index == null || index < 0 || index >= lines.size() ? null : lines.get(index);
    }

    public static final class Builder<S> {
        private final ScriptHost<S> host;
        private BuildEnvironment environment = BuildEnvironment.EMPTY;
        private final List<Consumer<ScriptRegistrar>> registrations = new ArrayList<>();
        private Function<ScriptEngine<S>, List<CommandPreProcessor>> preProcessors =
                engine -> List.of(engine.aliases());

        private Builder(ScriptHost<S> host) {
            this.host = Objects.requireNonNull(host);
        }

        /** What the host's argument types are made with. Defaults to {@link BuildEnvironment#EMPTY}. */
        public Builder<S> environment(BuildEnvironment environment) {
            this.environment = Objects.requireNonNull(environment);
            return this;
        }

        /** Registers types and nodes. Can be given more than once; each runs in turn, in the same build. */
        public Builder<S> register(Consumer<ScriptRegistrar> registrations) {
            this.registrations.add(Objects.requireNonNull(registrations));
            return this;
        }

        /**
         * The pre-processors each line goes through, in order, made from the built engine. Defaults
         * to the engine's {@link ScriptEngine#aliases()} alone; include them when replacing it.
         */
        public Builder<S> preProcessors(Function<ScriptEngine<S>, List<CommandPreProcessor>> preProcessors) {
            this.preProcessors = Objects.requireNonNull(preProcessors);
            return this;
        }

        public ScriptRunner<S> build() {
            return new ScriptRunner<>(this);
        }
    }
}
