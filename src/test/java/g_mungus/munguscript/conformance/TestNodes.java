package g_mungus.munguscript.conformance;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.conformance.TestTypes.Point;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.ScriptNodes;

import static g_mungus.munguscript.conformance.TestTypes.CELSIUS;
import static g_mungus.munguscript.conformance.TestTypes.COLOR;
import static g_mungus.munguscript.conformance.TestTypes.COUNTER;
import static g_mungus.munguscript.conformance.TestTypes.POINT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.INT;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;

/** The getters, mappers and executors the tests run against, reading and writing one {@link World}. */
final class TestNodes {

    private TestNodes() {
    }

    static void register(ScriptRegistrar registrar, World world) {
        registrar.registerType(POINT);
        registrar.registerType(COLOR);
        registrar.registerType(CELSIUS);
        registrar.registerType(COUNTER);

        registrar.register(ScriptNodes.getter("origin", POINT, context -> new Point(0, 0)));
        registrar.register(ScriptNodes.getter("here", POINT, context -> context.host(TestHost.Place.class).pos()));
        registrar.register(ScriptNodes.getter("counter", COUNTER, context -> world.counter));
        registrar.register(ScriptNodes.getter("favourite", COLOR, context -> world.favourite));
        registrar.register(ScriptNodes.getter("level", INT, context -> world.level));
        registrar.register(ScriptNodes.getter("message", STRING, context -> world.message));
        registrar.register(ScriptNodes.getter("explode", INT, context -> {
            throw new IllegalStateException("the getter exploded");
        }));

        registrar.register(ScriptNodes.mapper("x", POINT, INT, (point, context) -> point.x()));
        registrar.register(ScriptNodes.mapper("y", POINT, INT, (point, context) -> point.y()));
        registrar.register(ScriptNodes.argumentMapper("plus", POINT, POINT, POINT,
                (point, context) -> point.plus(context.argumentValue(Point.class))));
        registrar.register(ScriptNodes.argumentMapper("scale", INT, INT, "factor", INT,
                (value, context) -> value * context.argumentValue(Integer.class)));
        registrar.register(ScriptNodes.mapper("is_positive", INT, BOOLEAN, (value, context) -> value > 0));
        registrar.register(ScriptNodes.mapper("to_celsius", INT, CELSIUS, (value, context) -> (double) value));
        registrar.register(ScriptNodes.mapper("warm", CELSIUS, BOOLEAN, (degrees, context) -> degrees > 20));
        registrar.register(ScriptNodes.mapper("crash", INT, INT, (value, context) -> {
            throw new ArithmeticException("the mapper crashed");
        }));
        registrar.register(ScriptNodes.mapper("not", BOOLEAN, BOOLEAN, (value, context) -> !value));
        registrar.register(ScriptNodes.mapper("name", COLOR, STRING, (color, context) -> COLOR.requireLiteral().print(color)));
        registrar.register(ScriptNodes.mapper("value", COUNTER, INT, (counter, context) -> counter.value));
        // A raw argument: no script type describes a regex, so it cannot be a value_of.
        registrar.register(ScriptNodes.rawArgumentMapper("matches", STRING, BOOLEAN, "pattern",
                StringArgumentType.string(), String.class,
                (text, context) -> text.matches(context.argumentValue(String.class))));

        registrar.register(ScriptNodes.executor("paint", COLOR, (color, context) -> world.record("paint", color)));
        registrar.register(ScriptNodes.executor("move", POINT, (point, context) -> world.record("move", point)));
        registrar.register(ScriptNodes.executor("set_level", INT, IntegerArgumentType.integer(0, 10),
                (value, context) -> {
                    world.level = value;
                    return world.record("set_level", value);
                }));
        registrar.register(ScriptNodes.executor("pick", INT, new TestTypes.ColorArgument(), Color.class,
                (color, context) -> color.ordinal(), (index, context) -> world.record("pick", index)));
        registrar.register(ScriptNodes.executor("log", STRING, (text, context) -> world.record("log", text)));
        registrar.register(ScriptNodes.executor("boom", INT, (value, context) -> {
            throw new IllegalStateException("the executor blew up");
        }));

        // One name, three overloads: two for particular blocks, one for anywhere else.
        registrar.register(ScriptNodes.executor("configure", INT,
                (value, context) -> world.record("configure_a", value)).withApplicability(TestHost.Blocks.of("block_a")));
        registrar.register(ScriptNodes.executor("configure", COLOR,
                (color, context) -> world.record("configure_b", color)).withApplicability(TestHost.Blocks.of("block_b")));
        registrar.register(ScriptNodes.executor("configure", INT,
                (value, context) -> world.record("configure_any", value)));
    }
}
