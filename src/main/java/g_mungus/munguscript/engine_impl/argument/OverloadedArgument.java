package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The argument of the executors that share a name, one variant per executor in registration
 * order. Brigadier allows one argument per literal, so the variants are tried here: those that read
 * the most of the command are kept (so {@code go 5 6} is a point, not an int followed by junk), and
 * which of them runs is left to the target when the command runs.
 */
public final class OverloadedArgument implements ArgumentType<ParsedOverload> {
    private final List<ValueOrLiteralArgument> variants;
    private final ArgumentLookup lookup;
    private final boolean runnable;

    /**
     * @param runnable false for an argument rebuilt on a client: it no longer knows its executors,
     *                 so what it parses must not be run
     */
    public OverloadedArgument(List<ValueOrLiteralArgument> variants, ArgumentLookup lookup, boolean runnable) {
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("An overloaded argument needs at least one variant");
        }
        this.variants = List.copyOf(variants);
        this.lookup = lookup;
        this.runnable = runnable;
    }

    public List<ValueOrLiteralArgument> variants() {
        return variants;
    }

    @Override
    public ParsedOverload parse(StringReader reader) throws CommandSyntaxException {
        Optional<Object> form = ValueOrLiteralArgument.readForm(reader);
        if (form.isPresent()) {
            return form.get() instanceof ValueOf valueOf ? valueOf(valueOf)
                    : ParsedOverload.forAll(variants.size(), form.get(), runnable);
        }
        Map<Integer, Object> longest = new LinkedHashMap<>();
        int furthest = -1;
        @Nullable CommandSyntaxException error = null;
        for (int i = 0; i < variants.size(); i++) {
            StringReader attempt = new StringReader(reader);
            try {
                Object parsed = variants.get(i).literal().parse(attempt);
                if (attempt.getCursor() > furthest) {
                    furthest = attempt.getCursor();
                    longest.clear();
                }
                if (attempt.getCursor() == furthest) {
                    longest.put(i, parsed);
                }
            } catch (CommandSyntaxException e) {
                // When nothing parses, the variant that got furthest has the most useful complaint.
                if (error == null || e.getCursor() > error.getCursor()) {
                    error = e;
                }
            }
        }
        if (longest.isEmpty()) {
            throw error;
        }
        reader.setCursor(furthest);
        return new ParsedOverload(longest, runnable);
    }

    /** A {@code value_of(...)} goes to the variants that take the type it gives. */
    private ParsedOverload valueOf(ValueOf valueOf) throws CommandSyntaxException {
        TypeKey gives = lookup.view().check(this, valueOf,
                variants.stream().map(ValueOrLiteralArgument::target).distinct().toList());
        Map<Integer, Object> taking = new LinkedHashMap<>();
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).target().equals(gives)) {
                taking.put(i, valueOf);
            }
        }
        return new ParsedOverload(taking, runnable);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return lookup.view().suggest(this, context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return variants.get(0).getExamples();
    }

    @Override
    public String toString() {
        return "overloaded" + variants;
    }
}
