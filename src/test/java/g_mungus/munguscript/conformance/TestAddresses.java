package g_mungus.munguscript.conformance;

import com.mojang.brigadier.context.StringRange;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.Rewriter;
import g_mungus.munguscript.engine.preprocess.Rewritten;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A host's address pre-processor, as a mod's would be: {@code @name} becomes a point. */
final class TestAddresses implements CommandPreProcessor {
    private static final Pattern ADDRESS = Pattern.compile("(?<![A-Za-z0-9._])@([A-Za-z0-9._]+)");
    private final Map<String, Point> addresses;

    TestAddresses(Map<String, Point> addresses) {
        this.addresses = Map.copyOf(addresses);
    }

    @Override
    public Prepared prepare(List<String> scriptLines, PreProcessContext context) {
        return new Prepared() {
            @Override
            public Set<Integer> consumedLines() {
                return Set.of();
            }

            @Override
            public List<PreProcessDiagnostic> diagnostics() {
                return List.of();
            }

            @Override
            public PreProcessed process(String command, PreProcessContext context) {
                Rewriter rewriter = new Rewriter(command);
                List<PreProcessDiagnostic> diagnostics = new ArrayList<>();
                Matcher matcher = ADDRESS.matcher(command);
                int kept = 0;
                while (matcher.find()) {
                    Point point = addresses.get(matcher.group(1));
                    if (point == null) {
                        diagnostics.add(PreProcessDiagnostic.inCommand("Unknown address @" + matcher.group(1),
                                StringRange.between(matcher.start(), matcher.end())));
                        continue;
                    }
                    rewriter.keep(kept, matcher.start()).replace(matcher.start(), matcher.end(), point.x() + " " + point.y());
                    kept = matcher.end();
                }
                Rewritten rewritten = rewriter.keep(kept, command.length()).build();
                return new PreProcessed(rewritten.text(), rewritten.map(), diagnostics);
            }

            @Override
            public Collection<PreProcessorToken> tokens() {
                return addresses.entrySet().stream()
                        .map(entry -> (PreProcessorToken) new PreProcessorToken("@" + entry.getKey(),
                                PreProcessorToken.Placement.ARGUMENT, TestTypes.POINT.key(),
                                entry.getValue().x() + " " + entry.getValue().y()))
                        .toList();
            }
        };
    }
}
