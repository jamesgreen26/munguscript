package g_mungus.munguscript.conformance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.conformance.TestHost.Source;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.conformance.World.Call;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.spi.EngineProvider;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One engine from one {@link EngineProvider}, hosted the way a Minecraft mod would host it: its
 * nodes grafted under a hidden literal, and script commands entered through {@code script <command>},
 * which starts a run.
 *
 * <p>Scripts go through the whole system, as a script terminal would run them: prepared and
 * pre-processed (the engine's aliases, then the host's addresses), run line by line until one
 * fails, and any failure described by the engine. Nothing here knows how the engine is built.
 */
final class Harness {
    static final Point POS = new Point(7, 8);
    static final Point ORIGIN = new Point(100, 200);
    static final String PREFIX = "script ";
    /** Redirects to the script root without starting a run. */
    static final String WITHOUT_A_RUN = "raw ";

    final EngineProvider provider;
    final World world = new World();
    final ScriptEngine<Source> engine;
    final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
    /** The node the engine was grafted under. */
    final CommandNode<Source> grafted;
    String block = "stone";
    Map<String, Point> addresses = Map.of();

    Harness(EngineProvider provider) {
        this(provider, TestNodes::register);
    }

    Harness(EngineProvider provider, BiConsumer<ScriptRegistrar, World> registrations) {
        this.provider = provider;
        engine = provider.engine(TestHost.INSTANCE, BuildEnvironment.EMPTY,
                registrar -> registrations.accept(registrar, world));
        grafted = LiteralArgumentBuilder.<Source>literal("internal").build();
        dispatcher.getRoot().addChild(grafted);
        engine.graft(grafted);
        dispatcher.register(LiteralArgumentBuilder.<Source>literal(PREFIX.strip())
                .forward(engine.scriptRoot(), context -> List.of(engine.begin(context.getSource())), false));
        dispatcher.register(LiteralArgumentBuilder.<Source>literal(WITHOUT_A_RUN.strip())
                .redirect(engine.scriptRoot()));
    }

    /** Another engine from the same provider, with a world of its own and these registrations. */
    Harness with(BiConsumer<ScriptRegistrar, World> registrations) {
        return new Harness(provider, registrations);
    }

    @Override
    public String toString() {
        return provider.getClass().getSimpleName();
    }

    Source source() {
        return new Source(new TestHost.Place(POS, ORIGIN, block), null);
    }

    PreProcessContext preProcessContext() {
        return new PreProcessContext(engine.probe(source()), source().place());
    }

    CommandPreProcessor preProcessors() {
        return CommandPreProcessor.chain(List.of(engine.aliases(), new TestAddresses(addresses)));
    }

    CommandPreProcessor.Prepared prepare(String script) {
        return preProcessors().prepare(lines(script), preProcessContext());
    }

    /**
     * What running a script did.
     *
     * @param results     what each command that ran returned, in order
     * @param failure     the failure that stopped the script, if one did
     * @param line        the line that failed or had problems, if one did
     * @param diagnostics problems found while preparing the script or pre-processing a command
     */
    record Outcome(List<Integer> results, @Nullable ScriptFailure failure, @Nullable Integer line,
                   List<PreProcessDiagnostic> diagnostics) {

        boolean ran() {
            return failure == null && diagnostics.isEmpty();
        }
    }

    /** Runs a script the way a terminal would, stopping at the first line with a problem. */
    Outcome execute(String script) {
        List<String> lines = lines(script);
        PreProcessContext context = preProcessContext();
        CommandPreProcessor.Prepared prepared = preProcessors().prepare(lines, context);
        if (!prepared.diagnostics().isEmpty()) {
            return new Outcome(List.of(), null, null, prepared.diagnostics());
        }
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (prepared.consumedLines().contains(i) || lines.get(i).isBlank()) {
                continue;
            }
            String command = lines.get(i).strip();
            PreProcessed processed = prepared.process(command, context);
            if (!processed.diagnostics().isEmpty()) {
                return new Outcome(results, null, i, processed.diagnostics());
            }
            try {
                results.add(dispatcher.execute(PREFIX + processed.command(), source()));
            } catch (Exception e) {
                ScriptFailure failure = engine.describe(e, command, processed.command(), processed.sourceMap());
                return new Outcome(results, failure, i, List.of());
            }
        }
        return new Outcome(results, null, null, List.of());
    }

    /** Runs a script that is expected to run in full, and returns what each command returned. */
    List<Integer> run(String script) {
        Outcome outcome = execute(script);
        if (!outcome.ran()) {
            throw new AssertionError("Expected the script to run, but line " + outcome.line() + " did not: "
                    + (outcome.failure() != null ? outcome.failure() : outcome.diagnostics()));
        }
        return outcome.results();
    }

    /** Runs a script that is expected to fail, and returns the failure. */
    ScriptFailure failure(String script) {
        Outcome outcome = execute(script);
        if (outcome.failure() == null) {
            throw new AssertionError("Expected the script to fail, but it gave " + outcome);
        }
        return outcome.failure();
    }

    /** Runs a script that is expected to have pre-processing problems, and returns them. */
    List<PreProcessDiagnostic> diagnostics(String script) {
        Outcome outcome = execute(script);
        if (outcome.diagnostics().isEmpty()) {
            throw new AssertionError("Expected pre-processing problems, but the script gave " + outcome);
        }
        return outcome.diagnostics();
    }

    /** Evaluates what a script would write as {@code value_of(expression)}, as the host's API does. */
    <T> T valueOf(String expression, ScriptType<T> type) {
        return engine.evaluate(expression, type, engine.begin(source()));
    }

    /** Whether the engine's view reads all of {@code command} without an error, running nothing. */
    boolean parsesFully(String command) {
        var parse = engine.parse(command, source());
        return parse.getExceptions().isEmpty() && !parse.getReader().canRead();
    }

    /** Runs a command through the script root without starting a run first. */
    int runWithoutARun(String command) throws CommandSyntaxException {
        return dispatcher.execute(WITHOUT_A_RUN + command, source());
    }

    /** The texts suggested at the end of {@code command}. */
    List<String> suggest(String command, @Nullable CommandPreProcessor.Prepared prepared) {
        return suggestions(command, prepared).stream().map(Suggestion::getText).toList();
    }

    List<Suggestion> suggestions(String command, @Nullable CommandPreProcessor.Prepared prepared) {
        return engine.suggest(command, command.length(), source(), prepared).join().getList();
    }

    void assertCalls(Call... expected) {
        assertEquals(List.of(expected), world.calls);
    }

    static List<String> lines(String script) {
        return Arrays.asList(script.stripTrailing().split("\n", -1));
    }
}
