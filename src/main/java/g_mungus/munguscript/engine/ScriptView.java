package g_mungus.munguscript.engine;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.ExpressionProbe;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
     * pre-processing is given, its tokens are offered as well. A cursor outside the command is taken
     * to be at its nearer end.
     */
    CompletableFuture<Suggestions> suggest(String command, int cursor, S source,
                                           @Nullable CommandPreProcessor.Prepared preProcessing);

    /**
     * What each word of a command is, for syntax highlighting, in order and without overlaps;
     * spaces are not covered. When a script's pre-processing is given, the command is pre-processed
     * first, and a word it rewrote is highlighted as its token: an alias as {@link Highlight.Kind#ALIAS},
     * a token that stands for an argument as {@link Highlight.Kind#ARGUMENT}.
     *
     * <p>A {@code value_of(...)} is an argument, but only {@code value_of(} and its {@code )} are
     * highlighted as one; the expression inside is highlighted word by word, even while it is
     * incomplete. Whatever cannot be read is {@link Highlight.Kind#UNPARSED}. This says nothing about
     * errors otherwise: a {@code value_of} of the wrong type is still highlighted as written, and its
     * problem comes from {@link #parse}.
     */
    List<Highlight> highlight(String command, S source, @Nullable CommandPreProcessor.Prepared preProcessing);

    /**
     * What each word of an expression is, as {@link #highlight} says for a command: an expression
     * as a {@code value_of(...)} holds it, or as {@link ScriptEngine#evaluate} is given it, starting
     * with a getter or an alias. When a script's pre-processing is given, aliases and other tokens in
     * it are expanded and highlighted as they are in a command.
     */
    List<Highlight> highlightExpression(String expression, S source,
                                        @Nullable CommandPreProcessor.Prepared preProcessing);

    /**
     * What each word of an alias definition's right-hand side is: the {@code body} of
     * {@code #def name = body}. A literal (a number, {@code true} or {@code false}, or a string) is
     * one {@link Highlight.Kind#ARGUMENT}; anything else is highlighted as {@link #highlightExpression}
     * does. As in the definition itself, a getter or alias of the same name is not a literal: a body
     * that names one is that getter or alias. Ranges are in {@code body}.
     *
     * @param preProcessing the script's pre-processing, whose aliases the body may use
     */
    List<Highlight> highlightDefinition(String body, S source, @Nullable CommandPreProcessor.Prepared preProcessing);

    /**
     * Suggestions at {@code cursor} in an expression standing alone, as {@link #suggest} gives them
     * inside a {@code value_of(}: an expression as {@link ScriptEngine#evaluate} is given it,
     * starting with a getter or an alias. Ranges are in {@code expression}. A cursor outside it is
     * taken to be at its nearer end.
     *
     * @param type          the type the expression should give: only what can lead to it is
     *                      offered. Null for any type.
     * @param preProcessing a script's pre-processing, whose tokens are offered as well
     */
    CompletableFuture<Suggestions> suggestExpression(String expression, int cursor, S source, @Nullable TypeKey type,
                                                     @Nullable CommandPreProcessor.Prepared preProcessing);

    /**
     * What is wrong with a command as it is written, without running it: whether pre-processing
     * it, if a script's pre-processing is given, finds a problem, and otherwise whether it parses to
     * the end, to something that can run. Empty if it does. The failure's range is in
     * {@code command}, as written.
     *
     * <p>This finds what is wrong with how a command is written. What only running it can find,
     * such as a value that is out of range or a file that is missing, it cannot.
     */
    Optional<ScriptFailure> check(String command, S source, @Nullable CommandPreProcessor.Prepared preProcessing);

    /** Answers whether expressions read as a type, for {@code source}. */
    ExpressionProbe probe(S source);

    /**
     * The engine's own pre-processor for alias definitions ({@code #def name = expression}) at the
     * top of a script. Usually first in a {@link CommandPreProcessor#chain}.
     */
    CommandPreProcessor aliases();
}
