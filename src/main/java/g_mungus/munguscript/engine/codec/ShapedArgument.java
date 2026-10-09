package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Reads an argument by its {@link ArgumentShape}, for a client without the host's own argument
 * type. It gives the text it read, since only the host could make its value.
 */
final class ShapedArgument implements ArgumentType<String> {
    private static final DynamicCommandExceptionType EXPECTED =
            new DynamicCommandExceptionType(what -> new LiteralMessage("Expected " + what));

    private final ArgumentShape shape;
    private final List<String> examples;

    ShapedArgument(ArgumentShape shape, List<String> examples) {
        this.shape = shape;
        this.examples = List.copyOf(examples);
    }

    ArgumentShape shape() {
        return shape;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        read(shape, reader);
        return reader.getString().substring(start, reader.getCursor());
    }

    private static void read(ArgumentShape shape, StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (shape instanceof ArgumentShape.Text text && text.kind() == ArgumentShape.Text.Kind.TOKEN) {
            while (reader.canRead() && reader.peek() != ' ' && reader.peek() != ')') {
                reader.skip();
            }
            if (reader.getCursor() == start) {
                throw EXPECTED.createWithContext(reader, describe(shape));
            }
        } else if (shape instanceof ArgumentShape.Matching matching) {
            while (reader.canRead() && reader.peek() != ' ' && reader.peek() != ')') {
                reader.skip();
            }
            if (!reader.getString().substring(start, reader.getCursor()).matches(matching.pattern())) {
                reader.setCursor(start);
                throw EXPECTED.createWithContext(reader, matching.description());
            }
        } else if (shape instanceof ArgumentShape.Word word) {
            if (!reader.readUnquotedString().equals(word.word())) {
                reader.setCursor(start);
                throw EXPECTED.createWithContext(reader, "'" + word.word() + "'");
            }
        } else if (shape instanceof ArgumentShape.OneOf oneOf) {
            String read = reader.readUnquotedString();
            for (String word : oneOf.words()) {
                if (oneOf.ignoreCase() ? word.equalsIgnoreCase(read) : word.equals(read)) {
                    return;
                }
            }
            reader.setCursor(start);
            throw EXPECTED.createWithContext(reader, "one of " + String.join(", ", oneOf.words()));
        } else if (shape instanceof ArgumentShape.Sequence sequence) {
            for (int i = 0; i < sequence.parts().size(); i++) {
                if (i > 0) {
                    if (!reader.canRead() || reader.peek() != ' ') {
                        throw EXPECTED.createWithContext(reader, describe(sequence.parts().get(i)));
                    }
                    reader.skip();
                }
                read(sequence.parts().get(i), reader);
            }
        } else if (shape instanceof ArgumentShape.Loose) {
            reader.readString();
        } else {
            // Brigadier's own types.
            shape.argumentType(List.of()).parse(reader);
        }
    }

    /** What a shape is, for saying it was expected: {@code a coordinate}, {@code 'to'}. */
    static String describe(ArgumentShape shape) {
        if (shape instanceof ArgumentShape.Matching matching) {
            return matching.description();
        } else if (shape instanceof ArgumentShape.Word word) {
            return "'" + word.word() + "'";
        } else if (shape instanceof ArgumentShape.OneOf oneOf) {
            return "one of " + String.join(", ", oneOf.words());
        } else if (shape instanceof ArgumentShape.Sequence sequence) {
            return describe(sequence.parts().get(0));
        } else if (shape instanceof ArgumentShape.Bool) {
            return "true or false";
        } else if (shape instanceof ArgumentShape.Text || shape instanceof ArgumentShape.Loose) {
            return "text";
        } else if (shape instanceof ArgumentShape.IntRange || shape instanceof ArgumentShape.LongRange) {
            return "a whole number";
        } else if (shape instanceof ArgumentShape.FloatRange || shape instanceof ArgumentShape.DoubleRange) {
            return "a number";
        }
        return "a value";
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        suggest(shape, reader, builder);
        return builder.buildFuture();
    }

    /** Suggests for the part of {@code shape} that reading stops in, or that ends where the input does. */
    private static void suggest(ArgumentShape shape, StringReader reader, SuggestionsBuilder builder) {
        int start = reader.getCursor();
        if (shape instanceof ArgumentShape.Sequence sequence) {
            for (int i = 0; i < sequence.parts().size(); i++) {
                if (i > 0) {
                    if (!reader.canRead() || reader.peek() != ' ') {
                        return;
                    }
                    reader.skip();
                }
                int partStart = reader.getCursor();
                try {
                    read(sequence.parts().get(i), reader);
                    if (reader.canRead()) {
                        continue;
                    }
                } catch (CommandSyntaxException e) {
                    // Suggest for the part that could not be read.
                }
                reader.setCursor(partStart);
                suggest(sequence.parts().get(i), reader, builder);
                return;
            }
            return;
        }
        String typed = builder.getInput().substring(start).toLowerCase(Locale.ROOT);
        SuggestionsBuilder here = builder.createOffset(start);
        for (String word : words(shape)) {
            if (word.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.add(here.suggest(word));
            }
        }
    }

    private static List<String> words(ArgumentShape shape) {
        if (shape instanceof ArgumentShape.Word word) {
            return List.of(word.word());
        } else if (shape instanceof ArgumentShape.OneOf oneOf) {
            return oneOf.words();
        } else if (shape instanceof ArgumentShape.Bool) {
            return List.of("true", "false");
        }
        return List.of();
    }

    @Override
    public Collection<String> getExamples() {
        return examples;
    }

    @Override
    public String toString() {
        return "shaped(" + shape + ")";
    }
}
