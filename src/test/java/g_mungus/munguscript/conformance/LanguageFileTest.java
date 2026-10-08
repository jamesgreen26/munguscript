package g_mungus.munguscript.conformance;

import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.ScriptViewHost;
import g_mungus.munguscript.engine.codec.PortableHostCodec;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A language written to a file and read back by a tool without the host's code: no host codec and
 * no script types, only what the file holds. The test types' arguments describe their shapes, so
 * the view read back should behave as the engine's own.
 */
class LanguageFileTest {

    /**
     * The tool's host. Applicabilities come back only as their text, which for the test host's
     * names the blocks, so this one can still match them.
     */
    private static final ScriptViewHost<TestHost.Source> TOOL = new ScriptViewHost<>() {
        @Override
        public Object hostContext(TestHost.Source source) {
            return TestHost.VIEW_ONLY.hostContext(source);
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            String block = context.host(TestHost.Place.class).block();
            return ((PortableHostCodec.Described) applicability).text().contains(block) ? Match.EXPLICIT : Match.NONE;
        }

        @Override
        public String defaultNamespace() {
            return "unused";
        }
    };

    private static byte[] write(Harness h) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ScriptLanguageFile(h.provider.arguments()).write(h.engine, h.grafted, "host", new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static ScriptView<TestHost.Source> read(Harness h, byte[] bytes) throws IOException {
        return new ScriptLanguageFile(h.provider.arguments())
                .read(new DataInputStream(new ByteArrayInputStream(bytes)), namespace -> {
                    assertEquals("host", namespace);
                    return TOOL;
                });
    }

    private static ScriptView<TestHost.Source> roundTrip(Harness h) throws IOException {
        return read(h, write(h));
    }

    private static List<String> suggest(ScriptView<TestHost.Source> view, Harness h, String command) {
        return view.suggest(command, command.length(), h.source(), null).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    private static String checked(ScriptView<TestHost.Source> view, Harness h, String command,
                                  CommandPreProcessor.Prepared prepared) {
        Optional<ScriptFailure> failure = view.check(command, h.source(), prepared);
        return failure.map(f -> f.reason() + " at " + f.faultRange()).orElse("ok");
    }

    @EngineTest
    void aViewReadFromAFileHighlightsAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> tool = roundTrip(h);
        for (String command : CodecTest.COMMANDS) {
            assertEquals(HighlightTest.highlights(h.engine, h, command, null),
                    HighlightTest.highlights(tool, h, command, null), command);
        }
        for (String body : List.of("\"hello\"", "5", "level", "level scale 2", "target/site")) {
            assertEquals(HighlightTest.definitionHighlights(h.engine, h, body, null),
                    HighlightTest.definitionHighlights(tool, h, body, null), body);
        }
    }

    @EngineTest
    void aViewReadFromAFileSuggestsAsTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> tool = roundTrip(h);
        for (String command : CodecTest.COMMANDS) {
            for (int end = 0; end <= command.length(); end++) {
                String typed = command.substring(0, end);
                assertEquals(suggest(h.engine, h, typed), suggest(tool, h, typed), "'" + typed + "'");
            }
        }
    }

    @EngineTest
    void aViewReadFromAFileFindsWhatTheEnginesOwnViewDoes(Harness h) throws IOException {
        ScriptView<TestHost.Source> tool = roundTrip(h);
        CommandPreProcessor.Prepared engineAliases = h.engine.aliases().prepare(
                List.of("#def lvl = level", "#def greeting = \"x\""), h.preProcessContext());
        CommandPreProcessor.Prepared toolAliases = tool.aliases().prepare(
                List.of("#def lvl = level", "#def greeting = \"x\""), h.preProcessContext());
        for (String command : CodecTest.COMMANDS) {
            assertEquals(checked(h.engine, h, command, engineAliases), checked(tool, h, command, toolAliases), command);
        }
        assertEquals("ok", checked(tool, h, "set_level value_of(lvl scale 2)", toolAliases));
        assertEquals("ok", checked(tool, h, "log value_of(greeting + \"y\")", toolAliases));
    }

    @EngineTest
    void checkFindsWhatIsWrongWithACommand(Harness h) {
        assertEquals("ok", checked(h.engine, h, "paint red", null));
        assertEquals("Unknown command 'frobnicate' at StringRange{start=0, end=10}",
                checked(h.engine, h, "frobnicate", null));
        assertTrue(checked(h.engine, h, "paint", null).startsWith("The command is incomplete"));
    }

    @EngineTest
    void typesComeBackWithWhatTheyAreUsableAs(Harness h) throws IOException {
        ScriptView<TestHost.Source> tool = roundTrip(h);
        assertTrue(tool.type(TestTypes.CELSIUS.key()).isPresent());
        // Celsius is usable as a double, so a double's words follow it.
        assertTrue(suggest(tool, h, "if level to_celsius >").contains(">"));
        assertEquals("ok", checked(tool, h, "if level > value_of(level to_celsius) log x", null));
    }

    @EngineTest
    void somethingElseIsNotALanguageFile(Harness h) throws IOException {
        byte[] bytes = write(h);
        bytes[0] = 'X';
        IOException failure = assertThrows(IOException.class, () -> read(h, bytes));
        assertEquals("Not a MungusScript language file", failure.getMessage());
    }
}
