package g_mungus.munguscript.engine.preprocess;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Rewrites command text before the engine parses it: expanding aliases, replacing {@code @name}
 * addresses with coordinates, and the like.
 *
 * <p>Pre-processing happens in two steps. {@link #prepare} reads the whole script once, so that a
 * pre-processor can pick up definitions (alias lines) and report problems with them. The result
 * then rewrites each command and lists the tokens it understands, for suggestions and
 * highlighting. A single command with no script around it is prepared from an empty list.
 *
 * <p>Several pre-processors run as one through
 * {@link #chain}. Order matters: aliases before addresses,
 * so an alias body can use an address.
 */
public interface CommandPreProcessor {

    /**
     * Runs pre-processors one after another, in list order, as one. Each sees the previous one's
     * output, and the source maps are combined so that ranges still point into the player's text.
     */
    static CommandPreProcessor chain(List<CommandPreProcessor> preProcessors) {
        return new PreProcessorChain(preProcessors);
    }

    Prepared prepare(List<String> scriptLines, PreProcessContext context);

    /** A pre-processor ready for the commands of one script. */
    interface Prepared {

        /**
         * Indices of script lines this pre-processor used up, such as alias definitions. They are
         * not run as commands.
         */
        Set<Integer> consumedLines();

        /** Problems found while preparing, such as a malformed definition. */
        List<PreProcessDiagnostic> diagnostics();

        /** Rewrites one command. A command it has nothing to do with comes back unchanged. */
        PreProcessed process(String command, PreProcessContext context);

        /** The tokens this pre-processor will rewrite, offered as suggestions and coloured by the editor. */
        Collection<PreProcessorToken> tokens();
    }
}
