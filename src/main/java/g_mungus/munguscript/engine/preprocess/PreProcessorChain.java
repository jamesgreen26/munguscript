package g_mungus.munguscript.engine.preprocess;

import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.SourceMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pre-processors run one after another. Each prepares against the same script and rewrites the
 * previous one's output; the source maps are combined so everything points at the player's text.
 */
final class PreProcessorChain implements CommandPreProcessor {
    private final List<CommandPreProcessor> steps;

    PreProcessorChain(List<CommandPreProcessor> steps) {
        this.steps = List.copyOf(steps);
    }

    @Override
    public Prepared prepare(List<String> scriptLines, PreProcessContext context) {
        List<Prepared> prepared = new ArrayList<>();
        for (CommandPreProcessor step : steps) {
            prepared.add(step.prepare(scriptLines, context));
        }
        return new ChainPrepared(List.copyOf(prepared));
    }

    private record ChainPrepared(List<Prepared> steps) implements Prepared {

        @Override
        public Set<Integer> consumedLines() {
            Set<Integer> lines = new LinkedHashSet<>();
            steps.forEach(step -> lines.addAll(step.consumedLines()));
            return lines;
        }

        @Override
        public List<PreProcessDiagnostic> diagnostics() {
            List<PreProcessDiagnostic> diagnostics = new ArrayList<>();
            steps.forEach(step -> diagnostics.addAll(step.diagnostics()));
            return diagnostics;
        }

        @Override
        public PreProcessed process(String command, PreProcessContext context) {
            String current = command;
            SourceMap map = SourceMap.IDENTITY;
            List<PreProcessDiagnostic> diagnostics = new ArrayList<>();
            for (Prepared step : steps) {
                PreProcessed result = step.process(current, context);
                for (PreProcessDiagnostic diagnostic : result.diagnostics()) {
                    // Each step reports against its own input; move that onto the player's text.
                    diagnostics.add(diagnostic.range() == null ? diagnostic : new PreProcessDiagnostic(
                            diagnostic.message(), diagnostic.line(), map.toOriginal(diagnostic.range())));
                }
                map = SourceMap.compose(map, result.sourceMap());
                current = result.command();
            }
            return new PreProcessed(current, map, diagnostics);
        }

        @Override
        public Collection<PreProcessorToken> tokens() {
            List<PreProcessorToken> tokens = new ArrayList<>();
            steps.forEach(step -> tokens.addAll(step.tokens()));
            return tokens;
        }

        @Override
        public Prepared at(int line) {
            return new ChainPrepared(steps.stream().map(step -> step.at(line)).toList());
        }
    }
}
