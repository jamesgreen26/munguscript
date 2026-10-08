package g_mungus.munguscript.conformance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.codec.ScriptTreeCodec;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tree encoded to bytes on the server and decoded on a client by the library's own codec. */
class CodecTest {
    private static final List<ScriptType<?>> TYPES =
            List.of(TestTypes.POINT, TestTypes.COLOR, TestTypes.CELSIUS, TestTypes.COUNTER);

    /** Lines a client checks: plain, conditional, overloaded, nested value_of, and some that are wrong. */
    /** Where the first node starts: after the format version and the node count. */
    private static final int FIRST_NODE = 8;

    private static final List<String> COMMANDS = List.of(
            "paint red", "move 1 2", "move ~1 ~", "set_level 5", "set_level 11", "pick blue", "log \"hi\"",
            "configure 3", "configure red", "move value_of(here plus value_of(origin plus 1 1))",
            "set_level value_of(level scale 2)", "set_level value_of(here)", "set_level value_of(level",
            "if level is_positive paint red else paint blue", "paint red else paint blue",
            "if here x == 7 log a else if level > 0 log b else log c", "if level log x", "log value_of(message matches \"h\" as_string)",
            "frobnicate", "move value_of(", "configure value_of(lev", "log value_of(favourite)",
            "if level to_celsius rounded_down > 3 log x", "log value_of(level + \"x\")",
            "log value_of(counter lines)", "log value_of(literal_of(\"a b\") + \"c\")",
            "if literal_of(3) > 2 log big", "log value_of(literal_of(x y))", "if literal_of(5 log x",
            "log value_of(lit", "if level > 5.4 log x", "set_level value_of(level + 0.5)",
            "if level > value_of(level to_celsius) log x");

    private static byte[] encode(Harness h, TestHostCodec host) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ScriptTreeCodec(h.provider.arguments(), host).encode(h.engine, h.grafted, new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static ScriptView<TestHost.Source> decode(Harness h, byte[] bytes, TestHostCodec host) throws IOException {
        return new ScriptTreeCodec(h.provider.arguments(), host)
                .decode(new DataInputStream(new ByteArrayInputStream(bytes)), TestHost.VIEW_ONLY, TYPES);
    }

    private static ScriptView<TestHost.Source> roundTrip(Harness h) throws IOException {
        TestHostCodec host = new TestHostCodec();
        return decode(h, encode(h, host), host);
    }

    private static boolean parsesFully(ScriptView<TestHost.Source> view, Harness h, String command) {
        var parse = view.parse(command, h.source());
        return parse.getExceptions().isEmpty() && !parse.getReader().canRead();
    }

    private static List<String> suggest(ScriptView<TestHost.Source> view, Harness h, String command) {
        return view.suggest(command, command.length(), h.source(), null).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @EngineTest
    void aDecodedViewParsesAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        for (String command : COMMANDS) {
            assertEquals(parsesFully(h.engine, h, command), parsesFully(client, h, command), command);
        }
    }

    @EngineTest
    void aDecodedViewSuggestsAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        for (String command : COMMANDS) {
            for (int end = 0; end <= command.length(); end++) {
                String typed = command.substring(0, end);
                assertEquals(suggest(h.engine, h, typed), suggest(client, h, typed), "'" + typed + "'");
            }
        }
    }

    @EngineTest
    void aDecodedViewHighlightsAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        for (String command : COMMANDS) {
            assertEquals(HighlightTest.highlights(h.engine, h, command, null),
                    HighlightTest.highlights(client, h, command, null), command);
        }
        for (String expression : List.of("level scale value_of(level)", "here plus 1 2 x", "level sc",
                "level to_celsius > 20.5")) {
            assertEquals(HighlightTest.expressionHighlights(h.engine, h, expression, null),
                    HighlightTest.expressionHighlights(client, h, expression, null), expression);
        }
        for (String body : List.of("\"hello\"", "5", "true", "asdf", "level", "level scale 2", "target/site")) {
            assertEquals(HighlightTest.definitionHighlights(h.engine, h, body, null),
                    HighlightTest.definitionHighlights(client, h, body, null), body);
        }
    }

    @EngineTest
    void aDecodedViewSuggestsLoneExpressionsAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        for (String expression : List.of("", "lev", "level ", "here plus value_of(ori", "level to_celsius ")) {
            for (TypeKey type : Arrays.asList(null, TestTypes.POINT.key())) {
                assertEquals(SuggestionTest.suggestExpression(h.engine, h, expression, type, null),
                        SuggestionTest.suggestExpression(client, h, expression, type, null), expression + " " + type);
            }
        }
    }

    @EngineTest
    void aDecodedViewKnowsWhichWordsAreThereOnlyThroughAConversion(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        List<String> afterCelsius = suggest(client, h, "if level to_celsius ");
        assertTrue(afterCelsius.contains("warm"), afterCelsius.toString());
        assertFalse(afterCelsius.contains(">"), afterCelsius.toString());
        assertTrue(suggest(client, h, "if level to_celsius >").contains(">"));
    }

    @EngineTest
    void restrictionsTravelWithTheTree(Harness h) throws IOException {
        Harness r = RestrictionTest.restricted(h);
        ScriptView<TestHost.Source> client = roundTrip(r);
        assertFalse(suggest(client, r, "mi").contains("mine"));
        assertFalse(suggest(client, r, "set_level value_of(o").contains("ore"));
        r.block = "block_a";
        assertTrue(suggest(client, r, "mi").contains("mine"));
        assertTrue(suggest(client, r, "set_level value_of(o").contains("ore"));
    }

    @EngineTest
    void theHostWritesAndReadsOnlyItsOwnObjects(Harness h) throws IOException {
        Harness r = RestrictionTest.restricted(h);
        TestHostCodec host = new TestHostCodec();
        decode(r, encode(r, host), host);
        assertTrue(host.argumentsWritten > 0);
        assertEquals(host.argumentsWritten, host.argumentsRead);
        assertEquals(r.engine.restrictions().size(), host.applicabilitiesWritten);
        assertEquals(host.applicabilitiesWritten, host.applicabilitiesRead);
    }

    @EngineTest
    void aClientCanRedirectAPrefixOfItsOwnToTheDecodedScriptRoot(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        assertNotSame(h.engine.scriptRoot(), client.scriptRoot());
        CommandDispatcher<TestHost.Source> chat = new CommandDispatcher<>();
        chat.register(LiteralArgumentBuilder.<TestHost.Source>literal("script").redirect(client.scriptRoot()));
        String typed = "script move value_of(here plus value_of(or";
        List<String> suggested = chat.getCompletionSuggestions(chat.parse(typed, h.source())).join().getList()
                .stream().map(Suggestion::getText).toList();
        assertTrue(suggested.contains("origin"), suggested.toString());
    }

    @EngineTest
    void aDecodedTreeCannotRun(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        CommandDispatcher<TestHost.Source> chat = new CommandDispatcher<>();
        chat.register(LiteralArgumentBuilder.<TestHost.Source>literal("script").redirect(client.scriptRoot()));
        assertThrows(IllegalStateException.class, () -> chat.execute("script paint red", h.source()));
        h.assertCalls();
    }

    @EngineTest
    void everyNodeIsSentAndNoneAreMerged(Harness h) throws IOException {
        byte[] bytes = encode(h, new TestHostCodec());
        int count = ByteBuffer.wrap(bytes, 4, 4).getInt();
        assertEquals(reachable(h.grafted).size(), count);
        // Same-named nodes that redirect to different places compare equal by name alone, so merging
        // them would leave fewer nodes behind the decoded script root than behind the server's.
        assertEquals(reachable(h.engine.scriptRoot()).size(), reachable(roundTrip(h).scriptRoot()).size());
    }

    @EngineTest
    void decodedNodesKeepTheirChildrenInOrder(Harness h) throws IOException {
        ScriptView<TestHost.Source> client = roundTrip(h);
        assertEquals(childNames(h.engine.scriptRoot()), childNames(client.scriptRoot()));
        assertEquals(h.engine.scriptRoot().getChildren().stream().map(child -> child.getCommand() != null).toList(),
                client.scriptRoot().getChildren().stream().map(child -> child.getCommand() != null).toList());
    }

    @EngineTest
    void encodingIsRepeatable(Harness h) throws IOException {
        assertArrayEquals(encode(h, new TestHostCodec()), encode(h, new TestHostCodec()));
    }

    @EngineTest
    void anotherFormatVersionIsRefused(Harness h) throws IOException {
        byte[] bytes = encode(h, new TestHostCodec());
        ByteBuffer.wrap(bytes).putInt(0, ScriptTreeCodec.FORMAT_VERSION + 1);
        IOException e = assertThrows(IOException.class, () -> decode(h, bytes, new TestHostCodec()));
        assertTrue(e.getMessage().contains("format"), e.getMessage());
    }

    @EngineTest
    void truncatedBytesAreRefused(Harness h) throws IOException {
        byte[] bytes = encode(h, new TestHostCodec());
        for (int length : List.of(0, 6, bytes.length / 2, bytes.length - 1)) {
            byte[] cut = Arrays.copyOf(bytes, length);
            assertThrows(EOFException.class, () -> decode(h, cut, new TestHostCodec()), "cut to " + length);
        }
    }

    @EngineTest
    void aChildOutsideTheTableIsRefused(Harness h) throws IOException {
        byte[] bytes = encode(h, new TestHostCodec());
        // The first node: kind, flags, name, whether it runs, then its child count and first child.
        int nameLength = ByteBuffer.wrap(bytes, FIRST_NODE + 2, 2).getShort();
        int firstChild = FIRST_NODE + 2 + 2 + nameLength + 1 + 4;
        ByteBuffer.wrap(bytes).putInt(firstChild, Integer.MAX_VALUE);
        IOException e = assertThrows(IOException.class, () -> decode(h, bytes, new TestHostCodec()));
        assertTrue(e.getMessage().contains("nodes"), e.getMessage());
    }

    @EngineTest
    void unknownNodeFlagsAreRefused(Harness h) throws IOException {
        byte[] bytes = encode(h, new TestHostCodec());
        bytes[FIRST_NODE + 1] = (byte) 0x80;
        IOException e = assertThrows(IOException.class, () -> decode(h, bytes, new TestHostCodec()));
        assertTrue(e.getMessage().contains("unknown flags"), e.getMessage());
    }

    @EngineTest
    void aClientsOwnDispatcherSuggestsConvertedWordsOnlyOnceStarted(Harness h) throws IOException {
        // A client that leads its own command into the decoded tree and lets Brigadier suggest the rest.
        ScriptView<TestHost.Source> client = roundTrip(h);
        CommandDispatcher<TestHost.Source> chat = new CommandDispatcher<>();
        chat.register(LiteralArgumentBuilder.<TestHost.Source>literal("script").redirect(client.scriptRoot()));
        List<String> afterCelsius = brigadierSuggestions(chat, "script if level to_celsius ", h);
        assertTrue(afterCelsius.contains("warm"), afterCelsius.toString());
        assertFalse(afterCelsius.contains(">"), afterCelsius.toString());
        assertTrue(brigadierSuggestions(chat, "script if level to_celsius r", h).contains("rounded_down"));
    }

    private static List<String> brigadierSuggestions(CommandDispatcher<TestHost.Source> dispatcher, String command,
                                                     Harness h) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, h.source())).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    private static List<String> childNames(CommandNode<?> node) {
        return node.getChildren().stream().map(CommandNode::getName).toList();
    }

    private static <S> List<CommandNode<S>> reachable(CommandNode<S> root) {
        List<CommandNode<S>> found = new ArrayList<>();
        Set<CommandNode<S>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<CommandNode<S>> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            CommandNode<S> node = pending.pop();
            if (!seen.add(node)) continue;
            found.add(node);
            pending.addAll(node.getChildren());
            if (node.getRedirect() != null) pending.push(node.getRedirect());
        }
        return found;
    }
}
