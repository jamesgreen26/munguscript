package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.language.node.Applicability;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortableHostCodecTest {
    private static final PortableHostCodec CODEC = new PortableHostCodec();

    /** {@code "a" to "b"}, as a host would describe it. */
    private static final class TransferArgument implements ArgumentType<String>, PortableArgument {
        @Override
        public String parse(StringReader reader) {
            throw new UnsupportedOperationException("Only the host's own code would read this");
        }

        @Override
        public ArgumentShape shape() {
            ArgumentShape path = new ArgumentShape.Text(ArgumentShape.Text.Kind.QUOTABLE);
            return new ArgumentShape.Sequence(List.of(path, new ArgumentShape.Word("to"), path));
        }

        @Override
        public Collection<String> getExamples() {
            return List.of("\"a\" to \"b\"");
        }
    }

    /** One the host did not describe. */
    private static final class Opaque implements ArgumentType<String> {
        @Override
        public String parse(StringReader reader) {
            throw new UnsupportedOperationException();
        }
    }

    private static ArgumentType<?> roundTrip(ArgumentType<?> type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CODEC.writeArgumentType(new DataOutputStream(bytes), type);
        return CODEC.readArgumentType(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static Object parse(ArgumentType<?> type, String text) throws CommandSyntaxException {
        return type.parse(new StringReader(text));
    }

    private static List<String> suggest(ArgumentType<?> type, String typed) {
        return type.listSuggestions(null, new SuggestionsBuilder(typed, 0)).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @Test
    void brigadiersOwnTypesComeBackAsThemselves() throws IOException {
        assertEquals(IntegerArgumentType.integer(1, 10), roundTrip(IntegerArgumentType.integer(1, 10)));
        assertEquals(StringArgumentType.greedyString().getType(),
                ((StringArgumentType) roundTrip(StringArgumentType.greedyString())).getType());
    }

    @Test
    void aSequenceReadsItsPartsSeparatedBySpaces() throws Exception {
        ArgumentType<?> transfer = roundTrip(new TransferArgument());
        assertEquals("\"a b\" to c", parse(transfer, "\"a b\" to c"));
        StringReader reader = new StringReader("a to b else x");
        transfer.parse(reader);
        assertEquals(" else x", reader.getRemaining());
        assertEquals("Expected 'to' at position 2: a <--[HERE]",
                assertThrows(CommandSyntaxException.class, () -> parse(transfer, "a from b")).getMessage());
        assertEquals(List.of("\"a\" to \"b\""), List.copyOf(transfer.getExamples()));
    }

    @Test
    void aSequenceSuggestsForThePartBeingWritten() throws IOException {
        ArgumentType<?> transfer = roundTrip(new TransferArgument());
        assertEquals(List.of("to"), suggest(transfer, "\"a\" "));
        assertEquals(List.of("to"), suggest(transfer, "\"a\" t"));
        assertEquals(List.of(), suggest(transfer, "\"a\" to "));
    }

    @Test
    void oneOfReadsAndSuggestsItsWords() throws Exception {
        ArgumentType<?> colors = new ArgumentShape.OneOf(List.of("red", "green"), true).argumentType(List.of());
        assertEquals("RED", parse(colors, "RED"));
        assertThrows(CommandSyntaxException.class, () -> parse(colors, "blue"));
        assertEquals(List.of("green"), suggest(colors, "g"));
    }

    @Test
    void aTokenStopsAtASpaceOrClosingBracket() throws Exception {
        ArgumentType<?> token = new ArgumentShape.Text(ArgumentShape.Text.Kind.TOKEN).argumentType(List.of());
        assertEquals("~1", parse(token, "~1 2"));
        assertEquals("~1", parse(token, "~1)"));
    }

    @Test
    void anUndescribedTypeIsReadLoosely() throws Exception {
        ArgumentType<?> loose = roundTrip(new Opaque());
        assertInstanceOf(ShapedArgument.class, loose);
        assertEquals("word", parse(loose, "word rest"));
        assertEquals("\"two words\"", parse(loose, "\"two words\" rest"));
    }

    @Test
    void anApplicabilityComesBackAsItsText() throws IOException {
        Applicability blocks = new Applicability() {
            @Override
            public String toString() {
                return "stone, dirt";
            }
        };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CODEC.writeApplicability(new DataOutputStream(bytes), blocks);
        assertEquals(new PortableHostCodec.Described("stone, dirt"),
                CODEC.readApplicability(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
