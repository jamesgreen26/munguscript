package g_mungus.munguscript.engine;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.ExpressionProbe;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * What can be done with a built tree without running anything: parsing, suggesting, checking
 * expressions. Available on the server, and on the client from the tree the server sent.
 *
 * @param <S> the command source
 */
public interface ScriptView<S> {

    Optional<ScriptType<?>> type(TypeKey key);

    /** A type's key as scripts and errors write it. */
    String typeName(TypeKey key);

    /**
     * The node a script command starts from. The host redirects to it after its own prefix. In a
     * view over a received tree it is the received node, so a client can give it a prefix of its
     * own, such as an editor's.
     */
    CommandNode<S> scriptRoot();

    /** Parses an already pre-processed command. */
    ParseResults<S> parse(String command, S source);

    /**
     * Suggestions at {@code cursor} in a command as the player is writing it. When a script's
     * pre-processing is given, its tokens are offered as well.
     */
    CompletableFuture<Suggestions> suggest(String command, int cursor, S source,
                                           @Nullable CommandPreProcessor.Prepared preProcessing);

    /** Answers whether expressions read as a type, for {@code source}. */
    ExpressionProbe probe(S source);

    /**
     * The engine's own pre-processor for alias definitions ({@code #def name = expression}) at the
     * top of a script. Usually first in a {@link CommandPreProcessor#chain}.
     */
    CommandPreProcessor aliases();
}
