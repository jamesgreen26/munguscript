package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine.ScriptEngine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.host.RunState;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.engine_impl.tree.NodeNames;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.Applicability;
import g_mungus.munguscript.language.node.ScriptContext;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.BuildEnvironment;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Behaviour of the engine that the conformance suite leaves open. */
class EngineInternalsTest {

    private record Source(@Nullable RunState state) {
    }

    private static final ScriptHost<Source> HOST = new ScriptHost<>() {
        @Override
        public @Nullable RunState runState(Source source) {
            return source.state();
        }

        @Override
        public Source withRunState(Source source, RunState state) {
            return new Source(state);
        }

        @Override
        public Object hostContext(Source source) {
            return "context";
        }

        @Override
        public Match match(Applicability applicability, ScriptContext context) {
            return Match.NONE;
        }

        @Override
        public String defaultNamespace() {
            return "test";
        }
    };

    private final List<Integer> said = new ArrayList<>();
    private final List<Integer> shouted = new ArrayList<>();
    private final IllegalStateException broken = new IllegalStateException("the getter broke");
    private final ScriptEngine<Source> engine = new DefaultEngineProvider().engine(HOST, BuildEnvironment.EMPTY,
            registrar -> {
                registrar.register(ScriptNodes.getter("yes", BuiltInTypes.BOOLEAN, context -> true));
                registrar.register(ScriptNodes.executor("say", BuiltInTypes.INT, (value, context) -> {
                    said.add(value);
                    return value * 10;
                }));
                registrar.register(ScriptNodes.executor("shout", BuiltInTypes.INT, (value, context) -> {
                    shouted.add(value);
                    return value * 100;
                }));
                registrar.register(ScriptNodes.getter("one", BuiltInTypes.INT, context -> 1));
                registrar.register(ScriptNodes.getter("broken", BuiltInTypes.INT, context -> {
                    throw broken;
                }));
            });
    private final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();

    EngineInternalsTest() {
        CommandNode<Source> internal = LiteralArgumentBuilder.<Source>literal("internal").build();
        dispatcher.getRoot().addChild(internal);
        engine.graft(internal);
        dispatcher.register(LiteralArgumentBuilder.<Source>literal("script")
                .forward(engine.scriptRoot(), context -> List.of(engine.begin(context.getSource())), false));
    }

    private int run(String command) throws CommandSyntaxException {
        return dispatcher.execute("script " + command, new Source(null));
    }

    @Test
    void aBranchFollowedByElseStillReturnsItsResult() throws CommandSyntaxException {
        assertEquals(10, run("if yes say 1 else say 2"));
        assertEquals(20, run("unless yes say 1 else say 2"));
        assertEquals(List.of(1, 2), said);
    }

    @Test
    void eachElseRunsTheBranchItFollows() throws CommandSyntaxException {
        assertEquals(10, run("if yes say 1 else shout 2"));
        assertEquals(200, run("unless yes say 1 else shout 2"));
        assertEquals(300, run("unless yes say 1 else unless yes say 2 else shout 3"));
        assertEquals(400, run("unless yes say 1 else if yes shout 4 else say 5"));
        assertEquals(List.of(1), said);
        assertEquals(List.of(2, 3, 4), shouted);
    }

    @Test
    void aFailingNodeKeepsItsExceptionAsTheCause() {
        RuntimeException inCondition = assertThrows(RuntimeException.class, () -> run("if broken > 0 say 1"));
        assertSame(broken, inCondition.getCause());
        RuntimeException inValueOf = assertThrows(RuntimeException.class,
                () -> run("say value_of(one + value_of(broken))"));
        assertSame(broken, inValueOf.getCause());
        assertEquals("Could not evaluate 'broken': the getter broke",
                engine.describe(inValueOf, "x", "x", SourceMap.IDENTITY).reason());
        assertEquals(List.of(), said);
    }

    @Test
    void elseWithoutAConditionDoesNotParseAndIsPointedAt() {
        CommandSyntaxException failure = assertThrows(CommandSyntaxException.class, () -> run("say 1 else say 2"));
        var described = engine.describe(failure, "say 1 else say 2", "say 1 else say 2", SourceMap.IDENTITY);
        assertEquals("'else' is not valid here", described.reason());
        assertEquals("else", described.faultText());
        assertEquals(List.of(), said);
    }

    @Test
    void aValueOfOfTheWrongTypeIsNamedForTheExecutorItIsFor() {
        var parse = engine.parse("say value_of(yes)", new Source(null));
        assertEquals("value_of(yes) gives boolean, but say needs int",
                parse.getExceptions().values().iterator().next().getRawMessage().getString());
    }

    @Test
    void nothingRunsWhenAnInternalNodeIsReachedDirectly() throws CommandSyntaxException {
        assertEquals(0, dispatcher.execute("internal " + NodeNames.SCRIPT + " say 1", new Source(null)));
        assertEquals(List.of(), said);
    }

    @Test
    void sameNamedGettersThatRedirectElsewhereAreNotEqual() {
        CommandNode<Source> inCondition = engine.scriptRoot().getChild("if").getRedirect().getChild("yes");
        CommandNode<Source> inValue = dispatcherNode(NodeNames.VALUE).getChild("yes");
        assertNotEquals(inCondition, inValue);
    }

    private CommandNode<Source> dispatcherNode(String name) {
        return dispatcher.getRoot().getChild("internal").getChild(name);
    }
}
