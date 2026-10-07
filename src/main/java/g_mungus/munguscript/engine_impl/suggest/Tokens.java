package g_mungus.munguscript.engine_impl.suggest;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.preprocess.PreProcessorToken;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** A script's pre-processor tokens, offered as suggestions where what they stand for fits. */
public record Tokens(Collection<PreProcessorToken> all) {

    public static final Tokens NONE = new Tokens(List.of());

    public Tokens {
        all = List.copyOf(all);
    }

    /** Tokens that stand in an argument slot taking one of {@code targets}, such as an address. */
    List<Suggestion> arguments(StringRange range, String typed, Collection<TypeKey> targets) {
        return matching(PreProcessorToken.Placement.ARGUMENT, range, typed, targets::contains);
    }

    /** Tokens that start an expression, such as an alias, whose type {@code fits}. */
    List<Suggestion> expressions(StringRange range, String typed, Predicate<TypeKey> fits) {
        return matching(PreProcessorToken.Placement.EXPRESSION, range, typed, fits);
    }

    private List<Suggestion> matching(PreProcessorToken.Placement placement, StringRange range, String typed,
                                      Predicate<TypeKey> fits) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(token -> token.placement() == placement)
                .filter(token -> token.valueType() == null || fits.test(token.valueType()))
                .filter(token -> token.text().toLowerCase(Locale.ROOT).startsWith(prefix))
                .map(token -> new Suggestion(range, token.text(), new LiteralMessage(token.description())))
                .toList();
    }
}
