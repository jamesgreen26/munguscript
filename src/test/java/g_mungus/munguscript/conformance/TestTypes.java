package g_mungus.munguscript.conformance;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import g_mungus.munguscript.engine.codec.ArgumentShape;
import g_mungus.munguscript.engine.codec.PortableArgument;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Types the tests register: none of them come from the engine or from Minecraft. */
final class TestTypes {

    record Point(int x, int y) {
        Point plus(Point other) {
            return new Point(x + other.x, y + other.y);
        }
    }

    /** A point as written, where {@code ~} makes a coordinate relative to the run's origin. */
    record PointInput(int x, boolean xRelative, int y, boolean yRelative) {
        Point resolve(Point origin) {
            return new Point(xRelative ? origin.x() + x : x, yRelative ? origin.y() + y : y);
        }
    }

    enum Color { RED, GREEN, BLUE }

    static final class Counter {
        int value;

        Counter(int value) {
            this.value = value;
        }
    }

    static final ScriptType<Point> POINT = ScriptType.writable(key("point"), Point.class, PointInput.class,
                    (input, context) -> input.resolve(context.host(TestHost.Place.class).origin()))
            .hint("coordinates")
            .argument(new PointArgument())
            .print(point -> point.x() + " " + point.y())
            .parse(TestTypes::parsePoint)
            .build();

    static final ScriptType<Color> COLOR = ScriptType.writable(key("color"), Color.class)
            .argument(new ColorArgument())
            .print(color -> color.name().toLowerCase(Locale.ROOT))
            .parse(text -> Color.valueOf(text.trim().toUpperCase(Locale.ROOT)))
            .build();

    static final ScriptType<Double> CELSIUS = ScriptType.writable(key("celsius"), Double.class)
            .argument(DoubleArgumentType.doubleArg())
            .parse(text -> Double.parseDouble(text.trim()))
            .build()
            .usableAs(BuiltInTypes.DOUBLE, degrees -> degrees);

    static final ScriptType<Counter> COUNTER = ScriptType.opaque(key("counter"), Counter.class);

    private TestTypes() {
    }

    static TypeKey key(String path) {
        return new TypeKey("test", path);
    }

    private static Point parsePoint(String text) {
        String[] parts = text.trim().split("\\s+");
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    static final class PointArgument implements ArgumentType<PointInput>, PortableArgument {
        @Override
        public ArgumentShape shape() {
            ArgumentShape coordinate = new ArgumentShape.Text(ArgumentShape.Text.Kind.TOKEN);
            return new ArgumentShape.Sequence(List.of(coordinate, coordinate));
        }

        @Override
        public PointInput parse(StringReader reader) throws CommandSyntaxException {
            boolean xRelative = relative(reader);
            int x = coordinate(reader, xRelative);
            reader.expect(' ');
            boolean yRelative = relative(reader);
            int y = coordinate(reader, yRelative);
            return new PointInput(x, xRelative, y, yRelative);
        }

        private static boolean relative(StringReader reader) {
            if (reader.canRead() && reader.peek() == '~') {
                reader.skip();
                return true;
            }
            return false;
        }

        private static int coordinate(StringReader reader, boolean relative) throws CommandSyntaxException {
            if (relative && (!reader.canRead() || reader.peek() == ' ')) {
                return 0;
            }
            return reader.readInt();
        }
    }

    static final class ColorArgument implements ArgumentType<Color>, PortableArgument {
        private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(new LiteralMessage("Unknown color"));

        @Override
        public ArgumentShape shape() {
            return new ArgumentShape.OneOf(Arrays.stream(Color.values())
                    .map(color -> color.name().toLowerCase(Locale.ROOT)).toList(), true);
        }

        @Override
        public Color parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            String word = reader.readUnquotedString();
            for (Color color : Color.values()) {
                if (color.name().equalsIgnoreCase(word)) {
                    return color;
                }
            }
            reader.setCursor(start);
            throw UNKNOWN.createWithContext(reader);
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            for (Color color : Color.values()) {
                String name = color.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        }
    }
}
