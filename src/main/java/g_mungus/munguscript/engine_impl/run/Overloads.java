package g_mungus.munguscript.engine_impl.run;

import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.language.node.Applicability;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;

/** The overload rule, and asking the host whether a node is meant for a run's target. */
public final class Overloads {

    private Overloads() {
    }

    /**
     * Which of {@code candidates} runs: the first that names the target explicitly, otherwise the
     * first meant for any target, otherwise none (-1).
     */
    public static int choose(List<Integer> candidates, IntFunction<Match> match) {
        int unrestricted = -1;
        for (int candidate : candidates) {
            Match answer = match.apply(candidate);
            if (answer == Match.EXPLICIT) {
                return candidate;
            }
            if (answer == Match.UNRESTRICTED && unrestricted < 0) {
                unrestricted = candidate;
            }
        }
        return unrestricted;
    }

    /** Whether a node with this applicability is meant for what {@code source} is aimed at. */
    public static <S> Match match(ScriptViewHost<S> host, @Nullable Applicability applicability, S source) {
        // The host is never asked about nodes that are meant for every target.
        return applicability == null ? Match.UNRESTRICTED : host.match(applicability, NodeContext.of(host, source));
    }
}
