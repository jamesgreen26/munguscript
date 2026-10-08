package g_mungus.munguscript.engine_impl.highlight;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.engine_impl.argument.CommandText;
import g_mungus.munguscript.engine_impl.argument.LiteralOfArgument;
import g_mungus.munguscript.engine_impl.argument.OverloadedArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOf;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.tree.NodeNames;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Says what each word of a command is, from where it stands in the tree once the command is
 * parsed: Brigadier records, for each redirect, the node it went on from, so each word's parent is
 * known. A {@code value_of(...)} is read in place, as the engine reads it, and its expression is
 * highlighted word by word. Where parsing stops, a {@code value_of(} is still read as far as it
 * goes, so an expression being written is highlighted while it is incomplete.
 */
public final class Highlighter<S> {
    private final ScriptTree<S> tree;
    private final CommandDispatcher<S> commands;
    private final ExpressionReader<S> expressions;

    public Highlighter(ScriptTree<S> tree, CommandDispatcher<S> commands, ExpressionReader<S> expressions) {
        this.tree = tree;
        this.commands = commands;
        this.expressions = expressions;
    }

    /**
     * @param written   the command as the player wrote it
     * @param processed the command after pre-processing, which is what is parsed
     * @param map       maps ranges in {@code processed} back to {@code written}
     * @param tokens    the pre-processor's tokens, which a rewritten word is highlighted as
     */
    public List<Highlight> highlight(String written, String processed, SourceMap map,
                                     Collection<PreProcessorToken> tokens, @Nullable S source) {
        List<Highlight> found = new ArrayList<>();
        ParseResults<S> parse = commands.parse(processed, source);
        words(parse.getContext(), processed, source, found);
        rest(processed, parse.getReader().getCursor(), processed.length(), source, found);
        return toWritten(found, written, processed, map, tokens);
    }

    /**
     * An expression, which is {@code processed} from {@code start} to {@code end}. It may stand
     * inside text of the caller's, such as the {@code value_of(...)} it was wrapped in to be
     * pre-processed: highlights are given relative to {@code offset} in {@code written}, and only
     * those inside the expression are kept.
     *
     * @param length the expression's length as written
     */
    public List<Highlight> highlightExpression(String written, String processed, int start, int end, SourceMap map,
                                               Collection<PreProcessorToken> tokens, int offset, int length,
                                               @Nullable S source) {
        List<Highlight> found = new ArrayList<>();
        ParseResults<S> parse = expressions.parse(processed, start, end, source);
        words(parse.getContext(), processed, source, found);
        rest(processed, parse.getReader().getCursor(), end, source, found);
        List<Highlight> inside = new ArrayList<>();
        for (Highlight highlight : toWritten(found, written, processed, map, tokens)) {
            int from = Math.max(highlight.range().getStart() - offset, 0);
            int to = Math.min(highlight.range().getEnd() - offset, length);
            if (from < to) {
                inside.add(new Highlight(StringRange.between(from, to), highlight.kind()));
            }
        }
        return inside;
    }

    /** Every word parsed in {@code context} and the contexts it redirected into. */
    private void words(CommandContextBuilder<S> context, String input, @Nullable S source, List<Highlight> found) {
        for (CommandContextBuilder<S> current = context; current != null; current = current.getChild()) {
            CommandNode<S> parent = current.getRootNode();
            for (ParsedCommandNode<S> parsed : current.getNodes()) {
                word(parent, parsed.getNode(), parsed.getRange(), input, source, found);
                parent = parsed.getNode();
            }
        }
    }

    private void word(CommandNode<S> parent, CommandNode<S> node, StringRange range, String input,
                      @Nullable S source, List<Highlight> found) {
        if (node instanceof ArgumentCommandNode<S, ?> argument && argument.getType() instanceof LiteralOfArgument) {
            // It stands as a getter does; only the literal between its brackets is an argument.
            int innerStart = range.getStart() + LiteralOfArgument.OPEN.length();
            found.add(new Highlight(StringRange.between(range.getStart(), innerStart), Highlight.Kind.GETTER));
            found.add(new Highlight(StringRange.between(innerStart, range.getEnd() - 1), Highlight.Kind.ARGUMENT));
            found.add(new Highlight(StringRange.between(range.getEnd() - 1, range.getEnd()), Highlight.Kind.GETTER));
            return;
        }
        if (node instanceof ArgumentCommandNode<S, ?> argument) {
            boolean takesValueOf = argument.getType() instanceof ValueOrLiteralArgument
                    || argument.getType() instanceof OverloadedArgument;
            if (takesValueOf && ValueOf.startsAt(input, range.getStart())) {
                valueOf(input, range.getStart(), source, found);
            } else {
                found.add(new Highlight(range, Highlight.Kind.ARGUMENT));
            }
            return;
        }
        Highlight.Kind kind;
        if (ScriptTree.isKeyword(node.getName())
                && (node.getRedirect() == tree.script() || node.getRedirect() == tree.condition())) {
            kind = Highlight.Kind.KEYWORD;
        } else if (ScriptTree.isExecutor(node)) {
            kind = Highlight.Kind.EXECUTOR;
        } else if (parent == tree.condition() || parent == tree.value() || expressions.isRoot(parent)) {
            kind = Highlight.Kind.GETTER;
        } else if (NodeNames.chainType(parent.getName()).isPresent()) {
            kind = Highlight.Kind.MAPPER;
        } else {
            // A host's own node, ahead of the engine's.
            return;
        }
        found.add(new Highlight(range, kind));
    }

    /**
     * A {@code value_of(} at {@code start}, its expression, and its {@code )} if it has one.
     *
     * @return where the {@code value_of(...)} ends: past its {@code )}, or at the end of the input
     */
    private int valueOf(String input, int start, @Nullable S source, List<Highlight> found) {
        int innerStart = start + ValueOf.OPEN.length();
        @Nullable ValueOf closed = closed(input, start);
        int innerEnd = closed == null ? input.length() : closed.innerEnd();
        found.add(new Highlight(StringRange.between(start, innerStart), Highlight.Kind.ARGUMENT));
        ParseResults<S> inner = expressions.parse(input, innerStart, innerEnd, source);
        words(inner.getContext(), input, source, found);
        rest(input, inner.getReader().getCursor(), innerEnd, source, found);
        if (closed == null) {
            return input.length();
        }
        found.add(new Highlight(StringRange.between(closed.innerEnd(), closed.end()), Highlight.Kind.ARGUMENT));
        return closed.end();
    }

    private static @Nullable ValueOf closed(String input, int start) {
        StringReader reader = new StringReader(input);
        reader.setCursor(start);
        try {
            return ValueOf.read(reader);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    /** What was not parsed, from {@code from} to {@code to}: a {@code value_of(} still reads as far as it goes. */
    private void rest(String input, int from, int to, @Nullable S source, List<Highlight> found) {
        int start = CommandText.skipSpaces(input, from);
        if (start >= to) {
            return;
        }
        if (ValueOf.startsAt(input, start)) {
            rest(input, valueOf(input, start, source, found), to, source, found);
            return;
        }
        int end = to;
        while (end > start && input.charAt(end - 1) == ' ') {
            end--;
        }
        found.add(new Highlight(StringRange.between(start, end), Highlight.Kind.UNPARSED));
    }

    /**
     * Maps highlights back to the command as written. Every word of an expansion maps to the whole
     * token that was expanded, which is highlighted once, as the token. Where ranges overlap, the
     * earlier one wins.
     */
    private static List<Highlight> toWritten(List<Highlight> found, String written, String processed, SourceMap map,
                                             Collection<PreProcessorToken> tokens) {
        Map<String, PreProcessorToken> byText = new HashMap<>();
        tokens.forEach(token -> byText.put(token.text(), token));
        List<Highlight> mapped = new ArrayList<>();
        for (Highlight highlight : found) {
            StringRange range = map.toOriginal(highlight.range());
            if (range.isEmpty()) {
                continue;
            }
            String word = range.get(written);
            PreProcessorToken token = byText.get(word);
            // Only where it was rewritten: an argument written like an alias is still an argument.
            boolean rewritten = !highlight.range().get(processed).equals(word);
            Highlight.Kind kind = token == null || !rewritten ? highlight.kind()
                    : token.placement() == PreProcessorToken.Placement.EXPRESSION ? Highlight.Kind.ALIAS
                    : Highlight.Kind.ARGUMENT;
            mapped.add(new Highlight(range, kind));
        }
        mapped.sort(Comparator.comparingInt(highlight -> highlight.range().getStart()));
        List<Highlight> result = new ArrayList<>();
        int covered = 0;
        for (Highlight highlight : mapped) {
            int start = Math.max(highlight.range().getStart(), covered);
            if (start >= highlight.range().getEnd()) {
                continue;
            }
            result.add(start == highlight.range().getStart() ? highlight
                    : new Highlight(StringRange.between(start, highlight.range().getEnd()), highlight.kind()));
            covered = highlight.range().getEnd();
        }
        return result;
    }
}
