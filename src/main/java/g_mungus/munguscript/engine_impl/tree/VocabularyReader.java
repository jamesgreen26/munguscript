package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.engine_impl.argument.OverloadedArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Reads a {@link Vocabulary} off a script tree. */
public final class VocabularyReader {

    private VocabularyReader() {
    }

    public static <S> Vocabulary read(ScriptTree<S> tree, Collection<ScriptType<?>> types) {
        List<Vocabulary.Word> executors = new ArrayList<>();
        for (CommandNode<S> word : tree.script().getChildren()) {
            if (word instanceof LiteralCommandNode<?> && !ScriptTree.isKeyword(word.getName())) {
                executors.add(new Vocabulary.Word(word.getName(), null, forms(tree, word, false)));
            }
        }
        List<Vocabulary.Word> getters = new ArrayList<>();
        for (CommandNode<S> word : tree.value().getChildren()) {
            // literal_of(...) is an argument node, and a part of the language rather than a word.
            if (word instanceof LiteralCommandNode<?>) {
                getters.add(new Vocabulary.Word(word.getName(), null,
                        List.of(new Vocabulary.Form(null, List.of(), ScriptTree.outputOf(word).orElse(null)))));
            }
        }
        List<Vocabulary.Word> mappers = new ArrayList<>();
        // By type, since a received tree does not keep the order its chains were made in.
        List<Map.Entry<TypeKey, CommandNode<S>>> chains = new ArrayList<>(tree.valueChains().entrySet());
        chains.sort(Comparator.comparing(chain -> chain.getKey().toString()));
        for (Map.Entry<TypeKey, CommandNode<S>> chain : chains) {
            for (CommandNode<S> word : chain.getValue().getChildren()) {
                if (word instanceof LiteralCommandNode<?> && !tree.isConverted(word)) {
                    mappers.add(new Vocabulary.Word(word.getName(), chain.getKey(), forms(tree, word, true)));
                }
            }
        }
        List<Vocabulary.Type> described = types.stream()
                .map(type -> new Vocabulary.Type(type.key(),
                        type.conversions().stream().map(conversion -> conversion.target().key()).toList()))
                .toList();
        return new Vocabulary(executors, getters, mappers, described);
    }

    /**
     * Each argument the word may take, or that it takes none. An argument it takes only through a
     * conversion is left out: the conversion says so already.
     */
    private static <S> List<Vocabulary.Form> forms(ScriptTree<S> tree, CommandNode<S> word, boolean gives) {
        List<ArgumentCommandNode<S, ?>> arguments = ScriptTree.argumentsOf(word).stream()
                .filter(argument -> !tree.isConverted(argument))
                .toList();
        if (arguments.isEmpty()) {
            return List.of(new Vocabulary.Form(null, List.of(), gives ? ScriptTree.typeAfter(word).orElse(null) : null));
        }
        List<Vocabulary.Form> forms = new ArrayList<>();
        for (ArgumentCommandNode<S, ?> argument : arguments) {
            forms.add(new Vocabulary.Form(argument.getName(), takes(argument.getType()),
                    gives ? ScriptTree.typeAfter(argument).orElse(null) : null));
        }
        return forms;
    }

    private static List<TypeKey> takes(ArgumentType<?> type) {
        if (type instanceof ValueOrLiteralArgument slot) {
            return List.of(slot.target());
        } else if (type instanceof OverloadedArgument overloaded) {
            return overloaded.variants().stream().map(ValueOrLiteralArgument::target).distinct().toList();
        }
        return List.of();
    }
}
