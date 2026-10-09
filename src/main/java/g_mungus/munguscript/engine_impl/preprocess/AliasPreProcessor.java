package g_mungus.munguscript.engine_impl.preprocess;

import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.engine.preprocess.Rewriter;
import g_mungus.munguscript.engine.preprocess.Rewritten;
import g_mungus.munguscript.engine_impl.argument.LiteralOfArgument;
import g_mungus.munguscript.engine_impl.expression.ExpressionReader;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aliases: {@code #def name = expression} lines in a script. An alias is a text macro:
 * wherever an expression starts, its name is replaced with its expression, which runs again each
 * time. A failure inside the expansion points at the alias name, since the whole name maps to the
 * whole expansion.
 *
 * <p>An alias can also be a literal of a primitive type: a number, {@code true} or {@code false},
 * or a string, quoted or a bare word, as in {@code #def out = "target/site"}. A bare word that names
 * a getter or an earlier alias is that getter or alias, not a string. Like any alias it stands only
 * where an expression starts, as {@code literal_of(...)} around the literal; an argument written
 * like its name is still that argument.
 *
 * <p>Comments ({@code #} lines) and definitions may stand on any line, and are not run. A command
 * may use only the aliases defined above it ({@link Prepared#at}), and a definition only those
 * above it. A later definition of a name replaces the earlier one for the lines below it.
 */
public final class AliasPreProcessor<S> implements CommandPreProcessor {
    private static final String DEFINE = "#def";
    private static final String COMMENT = "#";
    private static final Pattern DECLARATION = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=(.*)");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final ScriptTree<S> tree;
    private final ExpressionReader<S> expressions;

    public AliasPreProcessor(ScriptTree<S> tree, ExpressionReader<S> expressions) {
        this.tree = tree;
        this.expressions = expressions;
    }

    @Override
    public Prepared prepare(List<String> scriptLines, PreProcessContext context) {
        Map<String, Alias> aliases = new LinkedHashMap<>();
        Set<Integer> consumed = new LinkedHashSet<>();
        List<PreProcessDiagnostic> diagnostics = new ArrayList<>();
        // The aliases as they stand after each line that defines one, for what the lines below may use.
        NavigableMap<Integer, Map<String, Alias>> scopes = new TreeMap<>();
        for (int i = 0; i < scriptLines.size(); i++) {
            String line = scriptLines.get(i).strip();
            if (!line.startsWith(COMMENT)) {
                continue;
            }
            consumed.add(i);
            if (isDefinition(line)) {
                int lineNumber = i;
                define(line.substring(DEFINE.length()).strip(), aliases)
                        .ifPresentOrElse(problem -> diagnostics.add(PreProcessDiagnostic.atLine(lineNumber, problem)),
                                () -> scopes.put(lineNumber, Map.copyOf(aliases)));
            }
        }
        return new AliasesPrepared(Map.copyOf(aliases), Set.copyOf(consumed), List.copyOf(diagnostics),
                Collections.unmodifiableNavigableMap(scopes));
    }

    private static boolean isDefinition(String line) {
        return line.startsWith(DEFINE) && (line.length() == DEFINE.length() || Character.isWhitespace(line.charAt(DEFINE.length())));
    }

    /** Adds the alias a declaration defines, or says what is wrong with it. */
    private Optional<String> define(String declaration, Map<String, Alias> aliases) {
        Matcher matcher = DECLARATION.matcher(declaration);
        if (!matcher.matches()) {
            return Optional.of("Invalid alias declaration");
        }
        String name = matcher.group(1);
        String body = matcher.group(2).strip();
        Optional<String> conflict = conflict(name);
        if (conflict.isPresent()) {
            return conflict;
        }
        if (body.isEmpty()) {
            return Optional.of("Alias expression cannot be empty");
        }
        // A getter or an earlier alias is tried first, so a literal never hides one of the same name.
        Optional<TypeKey> literal = aliases.containsKey(body) || tree.isGetter(body)
                ? Optional.empty() : LiteralOfArgument.literalType(body);
        if (literal.isPresent()) {
            aliases.put(name, new Alias(name, body, LiteralOfArgument.OPEN + body + ")", literal.get()));
            return Optional.empty();
        }
        Matcher first = NAME.matcher(body);
        boolean startsWell = LiteralOfArgument.startsAt(body, 0)
                || first.lookingAt() && (tree.isGetter(first.group()) || aliases.containsKey(first.group()));
        if (!startsWell) {
            return Optional.of("Alias must be a literal, or an expression that starts with a getter or previous alias");
        }
        // Earlier aliases are expanded now, so each expansion is complete on its own. That is also
        // what lets a definition replace an earlier one of the same name, even building on it,
        // while the definitions between them keep the one they were made with.
        String expansion = expand(body, ExpressionStarts.inExpression(body), aliases).text();
        aliases.put(name, new Alias(name, body, expansion, expressions.typeOf(expansion, null).orElse(null)));
        return Optional.empty();
    }

    private Optional<String> conflict(String name) {
        if (tree.isGetter(name)) {
            return Optional.of("Alias name '" + name + "' conflicts with a getter");
        }
        if (tree.isMapper(name)) {
            return Optional.of("Alias name '" + name + "' conflicts with a mapper");
        }
        if (ScriptTree.isKeyword(name)) {
            return Optional.of("Alias name '" + name + "' conflicts with a keyword");
        }
        return Optional.empty();
    }

    /** Replaces each alias name standing at one of {@code starts} with its expansion. */
    private static Rewritten expand(String text, List<Integer> starts, Map<String, Alias> aliases) {
        Rewriter rewriter = new Rewriter(text);
        int kept = 0;
        for (int start : starts) {
            Matcher name = NAME.matcher(text).region(start, text.length());
            if (!name.lookingAt() || !endsWord(text, name.end())) {
                continue;
            }
            Alias alias = aliases.get(name.group());
            if (alias != null) {
                rewriter.keep(kept, start).replace(start, name.end(), alias.expansion());
                kept = name.end();
            }
        }
        return rewriter.keep(kept, text.length()).build();
    }

    private static boolean endsWord(String text, int end) {
        return end == text.length() || text.charAt(end) == ' ' || text.charAt(end) == ')';
    }

    /**
     * @param body      the expression or literal as written, shown alongside the alias in suggestions
     * @param expansion what replaces the name: the expression with earlier aliases expanded, or
     *                  {@code literal_of(...)} around the literal
     * @param type      what it gives, if it reads in full
     */
    private record Alias(String name, String body, String expansion, @Nullable TypeKey type) {
    }

    /**
     * @param aliases every alias the script defines, as they stand at its end, for a command asked
     *                about with no line: a lone one, or one of the script's that is not placed
     * @param scopes  the aliases as they stand after each line that defines one
     */
    private record AliasesPrepared(Map<String, Alias> aliases, Set<Integer> consumedLines,
                                   List<PreProcessDiagnostic> diagnostics,
                                   NavigableMap<Integer, Map<String, Alias>> scopes) implements Prepared {

        /** Only the aliases defined above {@code line}. */
        @Override
        public Prepared at(int line) {
            Map.Entry<Integer, Map<String, Alias>> above = scopes.lowerEntry(line);
            return new AliasesPrepared(above == null ? Map.of() : above.getValue(), consumedLines, diagnostics, scopes);
        }

        @Override
        public PreProcessed process(String command, PreProcessContext context) {
            if (aliases.isEmpty()) {
                return PreProcessed.unchanged(command);
            }
            return PreProcessed.of(expand(command, ExpressionStarts.inCommand(command), aliases));
        }

        @Override
        public Collection<PreProcessorToken> tokens() {
            return aliases.values().stream()
                    .map(alias -> new PreProcessorToken(alias.name(), PreProcessorToken.Placement.EXPRESSION,
                            alias.type(), alias.body()))
                    .toList();
        }
    }
}
