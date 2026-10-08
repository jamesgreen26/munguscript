package g_mungus.munguscript.conformance;

import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.TypeKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The words a view lists, for showing a person what a language has. */
class VocabularyTest {

    private static Vocabulary.Word word(List<Vocabulary.Word> words, String name, TypeKey input) {
        return words.stream().filter(word -> word.name().equals(name) && Objects.equals(word.input(), input))
                .findFirst().orElseThrow(() -> new AssertionError("No " + name + " in " + words));
    }

    @EngineTest
    void executorsSayWhatTheyTake(Harness h) {
        Vocabulary vocabulary = h.engine.vocabulary();
        Vocabulary.Word paint = word(vocabulary.executors(), "paint", null);
        assertEquals(List.of(new Vocabulary.Form("color", List.of(TestTypes.COLOR.key()), null)), paint.forms());
        // Overloads share one argument, which takes each variant's type.
        Vocabulary.Word configure = word(vocabulary.executors(), "configure", null);
        assertEquals(1, configure.forms().size());
        assertTrue(configure.forms().get(0).takes().containsAll(
                List.of(BuiltInTypes.INT.key(), TestTypes.COLOR.key())), configure.toString());
        assertFalse(vocabulary.executors().stream().anyMatch(word -> word.name().equals("if")));
    }

    @EngineTest
    void gettersAndMappersSayWhatTheyGive(Harness h) {
        Vocabulary vocabulary = h.engine.vocabulary();
        assertEquals(List.of(new Vocabulary.Form(null, List.of(), BuiltInTypes.INT.key())),
                word(vocabulary.getters(), "level", null).forms());
        assertFalse(vocabulary.getters().stream().anyMatch(word -> word.name().startsWith("literal_of")));
        assertEquals(List.of(new Vocabulary.Form("factor", List.of(BuiltInTypes.INT.key()), BuiltInTypes.INT.key())),
                word(vocabulary.mappers(), "scale", BuiltInTypes.INT.key()).forms());
        assertEquals(List.of(new Vocabulary.Form(null, List.of(), TestTypes.CELSIUS.key())),
                word(vocabulary.mappers(), "to_celsius", BuiltInTypes.INT.key()).forms());
    }

    @EngineTest
    void aConversionsWordsAreListedOnlyUnderTheTypeThatHasThem(Harness h) {
        Vocabulary vocabulary = h.engine.vocabulary();
        // Celsius is usable as a double, but a double's words are a double's.
        assertTrue(vocabulary.types().contains(
                new Vocabulary.Type(TestTypes.CELSIUS.key(), List.of(BuiltInTypes.DOUBLE.key()))));
        assertFalse(vocabulary.mappers().stream().anyMatch(word -> TestTypes.CELSIUS.key().equals(word.input())
                && word.name().equals(">")), vocabulary.mappers().toString());
        // Nor are the arguments a word takes only through one, which the engine names for itself.
        assertFalse(vocabulary.mappers().stream().flatMap(word -> word.forms().stream())
                .anyMatch(form -> form.argument() != null && form.argument().contains(" as ")),
                vocabulary.mappers().toString());
    }

    @EngineTest
    void aLanguageFileListsTheSameWords(Harness h) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ScriptLanguageFile(h.provider.arguments()).write(h.engine, h.grafted, "host", new DataOutputStream(bytes));
        Vocabulary read = new ScriptLanguageFile(h.provider.arguments())
                .read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), namespace -> TestHost.VIEW_ONLY)
                .vocabulary();
        Vocabulary own = h.engine.vocabulary();
        assertEquals(own.executors(), read.executors());
        assertEquals(own.getters(), read.getters());
        assertEquals(own.mappers(), read.mappers());
        assertEquals(own.types().stream().map(type -> type.key().toString()).sorted().toList(),
                read.types().stream().map(type -> type.key().toString()).sorted().toList());
    }
}
