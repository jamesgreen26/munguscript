package g_mungus.munguscript.engine;

import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The words a language has, as a person would look them up: what each executor takes, what each
 * getter gives, and what each mapper follows, takes and gives. Read from the tree, so a view over
 * a received tree or a language file gives the same as the engine's own ({@link ScriptView#vocabulary}).
 *
 * <p>Executors and getters are in the order the tree holds them, and mappers by the type they
 * follow, then in that order. Mappers a type has only through a conversion are listed once, under
 * the type that has them, and an argument a word takes only through a conversion is left out.
 */
public record Vocabulary(List<Word> executors, List<Word> getters, List<Word> mappers, List<Type> types) {

    public Vocabulary {
        executors = List.copyOf(executors);
        getters = List.copyOf(getters);
        mappers = List.copyOf(mappers);
        types = List.copyOf(types);
    }

    /**
     * @param input the type a mapper follows; null for an executor or a getter
     * @param forms each way the word can be written, in the order they are tried
     */
    public record Word(String name, @Nullable TypeKey input, List<Form> forms) {
        public Word {
            forms = List.copyOf(forms);
        }
    }

    /**
     * One way to write a word.
     *
     * @param argument what the word takes after it, by its hint (e.g. {@code int}), or null if it
     *                 takes nothing
     * @param takes    the types the argument may be given as, written or as a {@code value_of(...)};
     *                 empty for an argument the host reads itself
     * @param gives    what the word gives; null for an executor
     */
    public record Form(@Nullable String argument, List<TypeKey> takes, @Nullable TypeKey gives) {
        public Form {
            takes = List.copyOf(takes);
        }
    }

    /** A type, and the types it is usable as without saying so. */
    public record Type(TypeKey key, List<TypeKey> usableAs) {
        public Type {
            usableAs = List.copyOf(usableAs);
        }
    }
}
