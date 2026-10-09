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

    /** Minecraft's block coordinates, as a host would describe them: three of {@code 5}, {@code ~}, {@code ~-2} or {@code ^1}. */
    private static final ArgumentShape BLOCK_POS;

    static {
        ArgumentShape coordinate = new ArgumentShape.Matching("[~^]|[~^]?-?\\d+", "a coordinate");
        BLOCK_POS = new ArgumentShape.Sequence(List.of(coordinate, coordinate, coordinate));
    }

    @Test
    void aMatchingPartReadsOneTokenThatFitsItsPattern() throws Exception {
        ArgumentType<?> pos = BLOCK_POS.argumentType(List.of());
        assertEquals("0 64 ~-3", parse(pos, "0 64 ~-3"));
        StringReader reader = new StringReader("~ ~1 ^ set_redstone 0");
        pos.parse(reader);
        assertEquals(" set_redstone 0", reader.getRemaining());
        assertEquals("~ ~1 ^", parse(pos, "~ ~1 ^)"));
        assertEquals("Expected a coordinate at position 2: 0 <--[HERE]",
                assertThrows(CommandSyntaxException.class, () -> parse(pos, "0 x 0")).getMessage());
        assertEquals("Expected a coordinate at position 4: 0 64<--[HERE]",
                assertThrows(CommandSyntaxException.class, () -> parse(pos, "0 64")).getMessage());
    }

    @Test
    void aHostGivesTheShapesOfTypesItCannotChange() throws Exception {
        PortableHostCodec codec = new PortableHostCodec(type -> type instanceof Opaque ? BLOCK_POS : null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        codec.writeArgumentType(new DataOutputStream(bytes), new Opaque());
        ArgumentType<?> read = codec.readArgumentType(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        // Without the host's shape it would be read loosely, and take only the 0.
        assertEquals("0 0 0", parse(read, "0 0 0"));
        assertThrows(CommandSyntaxException.class, () -> parse(read, "0"));
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
