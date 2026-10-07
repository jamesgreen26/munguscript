package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.TypeKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgumentFormsTest {
    private final ArgumentLookup lookup = new ArgumentLookup();

    private ValueOrLiteralArgument slot(ArgumentType<?> literal) {
        return new ValueOrLiteralArgument(literal, BuiltInTypes.INT.key(), lookup);
    }

    @Test
    void valueOfIsReadToItsMatchingParenthesis() throws CommandSyntaxException {
        StringReader reader = new StringReader("x value_of(a (\")\") b) rest");
        reader.setCursor(2);
        ValueOf valueOf = ValueOf.read(reader);
        assertEquals("a (\")\") b", valueOf.expression());
        assertEquals(StringRange.between(2, 21), valueOf.range());
        assertEquals(21, reader.getCursor());
    }

    @Test
    void anUnclosedValueOfFailsWhereItStarts() {
        StringReader reader = new StringReader("value_of(a");
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> ValueOf.read(reader));
        assertEquals(0, e.getCursor());
    }

    @Test
    void aSlotReadsAPlaceholderOnlyAsAWholeWord() throws CommandSyntaxException {
        assertEquals(new Placeholder(StringRange.between(0, 2)), slot(StringArgumentType.word()).parse(new StringReader("%s")));
        assertEquals("%sx", slot(StringArgumentType.greedyString()).parse(new StringReader("%sx")));
    }

    @Test
    void theOverloadsThatReadTheMostAreKept() throws CommandSyntaxException {
        OverloadedArgument overloaded = new OverloadedArgument(List.of(
                slot(IntegerArgumentType.integer()), slot(StringArgumentType.greedyString()),
                slot(StringArgumentType.string())), lookup, true);
        assertEquals(Map.of(1, "5 6"), overloaded.parse(new StringReader("5 6")).variants());
        assertEquals(Map.of(0, 5, 1, "5", 2, "5"), overloaded.parse(new StringReader("5")).variants());
    }

    @Test
    void aValueOfGoesToTheOverloadsThatTakeTheTypeItGives() throws CommandSyntaxException {
        lookup.pointAt(new ArgumentLookup.ArgumentView() {
            @Override
            public <S> CompletableFuture<Suggestions> suggest(ArgumentType<?> argument, CommandContext<S> context,
                                                              SuggestionsBuilder builder) {
                return Suggestions.empty();
            }

            @Override
            public TypeKey check(ArgumentType<?> argument, ValueOf valueOf, List<TypeKey> targets) {
                return BuiltInTypes.INT.key();
            }
        });
        OverloadedArgument overloaded = new OverloadedArgument(List.of(
                new ValueOrLiteralArgument(StringArgumentType.string(), BuiltInTypes.STRING.key(), lookup),
                slot(IntegerArgumentType.integer())), lookup, true);
        assertEquals(List.of(1), overloaded.parse(new StringReader("value_of(x)")).indices());
    }

    @Test
    void readingAValueOfBeforeThereIsAViewFails() {
        assertThrows(IllegalStateException.class,
                () -> slot(IntegerArgumentType.integer()).parse(new StringReader("value_of(x)")));
    }

    @Test
    void whenNoOverloadParsesTheFurthestComplaintIsKept() {
        OverloadedArgument overloaded = new OverloadedArgument(List.of(
                slot(IntegerArgumentType.integer(0, 3)), slot(IntegerArgumentType.integer(0, 5))), lookup, true);
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class,
                () -> overloaded.parse(new StringReader("9")));
        assertEquals(0, e.getCursor());
        assertEquals("Integer must not be more than 3, found 9", e.getRawMessage().getString());
    }

    @Test
    void suggestingBeforeThereIsAViewFails() {
        ValueOrLiteralArgument slot = slot(IntegerArgumentType.integer());
        assertThrows(IllegalStateException.class, () -> slot.listSuggestions(null,
                new SuggestionsBuilder("", 0)));
    }
}
