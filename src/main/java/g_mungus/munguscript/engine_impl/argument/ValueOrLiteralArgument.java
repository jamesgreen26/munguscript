package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An argument slot that takes a value of {@code target}: written as its {@code literal} argument,
 * as a {@code value_of(...)}, or as a {@code %s} placeholder. Parses to a {@link ValueOf}, a
 * {@link Placeholder}, or whatever the literal argument parses to.
 *
 * <p>A {@code value_of(...)} is read through the view while parsing, so one that cannot give a
 * {@code target} is a syntax error wherever it stands, even in a branch that would not run.
 */
public final class ValueOrLiteralArgument implements ArgumentType<Object> {
    private final ArgumentType<?> literal;
    private final TypeKey target;
    private final ArgumentLookup lookup;

    public ValueOrLiteralArgument(ArgumentType<?> literal, TypeKey target, ArgumentLookup lookup) {
        this.literal = literal;
        this.target = target;
        this.lookup = lookup;
    }

    public ArgumentType<?> literal() {
        return literal;
    }

    public TypeKey target() {
        return target;
    }

    @Override
    public Object parse(StringReader reader) throws CommandSyntaxException {
        Optional<Object> form = readForm(reader);
        if (form.isEmpty()) {
            return literal.parse(reader);
        }
        if (form.get() instanceof ValueOf valueOf) {
            lookup.view().check(this, valueOf, List.of(target));
        }
        return form.get();
    }

    /** A {@code value_of(...)} or {@code %s} at the cursor, which any variant of a slot accepts alike. */
    static Optional<Object> readForm(StringReader reader) throws CommandSyntaxException {
        String text = reader.getString();
        int start = reader.getCursor();
        if (ValueOf.startsAt(text, start)) {
            return Optional.of(ValueOf.read(reader));
        }
        int end = start + Placeholder.TEXT.length();
        if (text.startsWith(Placeholder.TEXT, start) && (end == text.length() || text.charAt(end) == ' ')) {
            reader.setCursor(end);
            return Optional.of(new Placeholder(StringRange.between(start, end)));
        }
        return Optional.empty();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return lookup.view().suggest(this, context, builder);
    }

    /** What the literal arguments of these slots suggest. */
    public static <S> List<Suggestion> written(Collection<ValueOrLiteralArgument> slots, CommandContext<S> context,
                                               SuggestionsBuilder builder) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (ValueOrLiteralArgument slot : slots) {
            SuggestionsBuilder own = new SuggestionsBuilder(builder.getInput(), builder.getStart());
            suggestions.addAll(slot.literal.listSuggestions(context, own).join().getList());
        }
        return suggestions;
    }

    /**
     * {@code value_of(}, if what has been typed could start it.
     *
     * @param alone whether nothing else is suggested. A client shows the slot's hint
     *              ({@code <coordinates>}) only when there are no suggestions, and the hint says more
     *              than {@code value_of(} does, so it is not offered alone until it has been started.
     */
    public static Optional<Suggestion> opening(SuggestionsBuilder builder, boolean alone) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (!ValueOf.OPEN.startsWith(typed) || alone && typed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Suggestion(StringRange.between(builder.getStart(), builder.getInput().length()),
                ValueOf.OPEN));
    }

    @Override
    public Collection<String> getExamples() {
        return literal.getExamples();
    }

    @Override
    public String toString() {
        return "value_or_literal(" + literal + ", " + target + ")";
    }
}
