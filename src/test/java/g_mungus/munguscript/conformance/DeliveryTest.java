package g_mungus.munguscript.conformance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.argument.ScriptArguments;
import g_mungus.munguscript.engine.host.Restriction;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tree sent to a client, rebuilt there with argument types the client made itself. */
class DeliveryTest {

    private static final List<ScriptType<?>> TYPES =
            List.of(TestTypes.POINT, TestTypes.COLOR, TestTypes.CELSIUS, TestTypes.COUNTER);

    private record Client(Transfer.Received<TestHost.Source> received, ScriptView<TestHost.Source> view) {
    }

    /** What a client has once it has decoded the tree and made its view. */
    private static Client receive(Harness h) {
        return receive(h, h.engine.restrictions());
    }

    private static Client receive(Harness h, List<Restriction> restrictions) {
        ScriptArguments.Rebuild rebuild = h.provider.arguments().rebuild();
        Transfer.Received<TestHost.Source> received = send(h, rebuild);
        return new Client(received, rebuild.view(TestHost.VIEW_ONLY, TYPES, restrictions, received.grafted()));
    }

    private static Transfer.Received<TestHost.Source> send(Harness h, ScriptArguments.Rebuild rebuild) {
        return Transfer.send(h.dispatcher.getRoot(), h.grafted, h.provider.arguments(), rebuild);
    }

    private static List<String> suggest(Harness h, ScriptView<TestHost.Source> view, String command) {
        return view.suggest(command, command.length(), h.source(), null).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @EngineTest
    void aClientSuggestsInsideValueOfOnceItsArgumentTypesPointAtTheView(Harness h) {
        Client client = receive(h);
        ScriptView<TestHost.Source> view = client.view();
        assertTrue(suggest(h, view, "move value_of(he").contains("here"));
        assertTrue(suggest(h, view, "paint value_of(favourite").contains(")"));
        assertTrue(suggest(h, view, "move value_of(here plus value_of(or").contains("origin"));
        assertTrue(suggest(h, view, "configure value_of(lev").contains("level"), "an overloaded executor");
        assertTrue(suggest(h, view, "if level > value_of(here ").contains("x"), "a condition's mapper");
    }

    @EngineTest
    void aClientParsesAndProbesValueOf(Harness h) {
        Client client = receive(h);
        var parse = client.view().parse("move value_of(here plus 1 1)", h.source());
        assertTrue(parse.getExceptions().isEmpty());
        assertFalse(parse.getReader().canRead());
        assertTrue(client.view().probe(h.source()).readsAs("here x > 1", BuiltInTypes.BOOLEAN.key()));
    }

    @EngineTest
    void theClientsOwnDispatcherSuggestsInsideValueOfOnceTheViewIsMade(Harness h) {
        ScriptArguments.Rebuild rebuild = h.provider.arguments().rebuild();
        Transfer.Received<TestHost.Source> received = send(h, rebuild);
        String typed = Harness.PREFIX + "move value_of(he";
        assertThrows(IllegalStateException.class, () -> chat(h, received, typed), "used before the view is made");
        rebuild.view(TestHost.VIEW_ONLY, TYPES, h.engine.restrictions(), received.grafted());
        assertTrue(chat(h, received, typed).contains("here"));
    }

    @EngineTest
    void aClientCanGiveTheReceivedScriptRootAPrefixOfItsOwn(Harness h) {
        Client client = receive(h);
        assertNotSame(h.engine.scriptRoot(), client.view().scriptRoot());
        CommandDispatcher<TestHost.Source> editor = new CommandDispatcher<>();
        editor.register(LiteralArgumentBuilder.<TestHost.Source>literal("edit").redirect(client.view().scriptRoot()));
        String typed = "edit move value_of(he";
        List<String> suggested = editor.getCompletionSuggestions(editor.parse(typed, h.source())).join().getList()
                .stream().map(Suggestion::getText).toList();
        assertTrue(suggested.contains("here"), suggested.toString());
    }

    @EngineTest
    void aViewOverATreeWithoutTheRebuiltArgumentsIsRefused(Harness h) {
        ScriptArguments.Rebuild rebuild = h.provider.arguments().rebuild();
        send(h, rebuild);
        assertThrows(IllegalArgumentException.class,
                () -> rebuild.view(TestHost.VIEW_ONLY, TYPES, h.engine.restrictions(), h.grafted));
    }

    @EngineTest
    void aRebuildMakesOneViewAndNothingAfterIt(Harness h) {
        ScriptArguments.Rebuild rebuild = h.provider.arguments().rebuild();
        Transfer.Received<TestHost.Source> received = send(h, rebuild);
        rebuild.view(TestHost.VIEW_ONLY, TYPES, h.engine.restrictions(), received.grafted());
        assertThrows(IllegalStateException.class,
                () -> rebuild.view(TestHost.VIEW_ONLY, TYPES, h.engine.restrictions(), received.grafted()));
        assertThrows(IllegalStateException.class, () -> send(h, rebuild));
    }

    @EngineTest
    void runningARebuiltOverloadFailsSayingWhy(Harness h) {
        ScriptArguments.Rebuild rebuild = h.provider.arguments().rebuild();
        var received = Transfer.sendRunnable(h.dispatcher.getRoot(), h.grafted, h.provider.arguments(), rebuild);
        rebuild.view(TestHost.VIEW_ONLY, TYPES, h.engine.restrictions(), received.grafted());
        var failure = assertThrows(IllegalStateException.class,
                () -> received.dispatcher().execute(Harness.PREFIX + "configure 3", h.source()));
        assertTrue(failure.getMessage().contains("rebuilt on a client"), failure.getMessage());
    }

    /** What the client's own dispatcher suggests, as chat would. */
    private static List<String> chat(Harness h, Transfer.Received<TestHost.Source> received, String typed) {
        var dispatcher = received.dispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(typed, h.source())).join().getList().stream()
                .map(Suggestion::getText).toList();
    }

    @EngineTest
    void literalArgumentsStillSuggestAsTheyDidOnTheServer(Harness h) {
        Client client = receive(h);
        assertTrue(suggest(h, client.view(), "paint b").contains("blue"));
        h.block = "block_b";
        assertTrue(suggest(h, client.view(), "configure b").contains("blue"));
        assertTrue(suggest(h, client.view(), "paint v").contains("value_of("));
    }

    @EngineTest
    void aClientGivenTheRestrictionsOnlySuggestsNodesWhereTheyApply(Harness h) {
        Harness r = RestrictionTest.restricted(h);
        Client client = receive(r);
        assertFalse(suggest(r, client.view(), "mi").contains("mine"));
        assertFalse(suggest(r, client.view(), "set_level value_of(o").contains("ore"));
        assertFalse(suggest(r, client.view(), "configure b").contains("blue"));
        r.block = "block_a";
        assertTrue(suggest(r, client.view(), "mi").contains("mine"));
        assertTrue(suggest(r, client.view(), "set_level value_of(o").contains("ore"));
    }

    @EngineTest
    void aClientWithoutTheRestrictionsSuggestsEverything(Harness h) {
        Harness r = RestrictionTest.restricted(h);
        Client client = receive(r, List.of());
        assertTrue(suggest(r, client.view(), "mi").contains("mine"));
        assertTrue(suggest(r, client.view(), "configure b").contains("blue"));
    }

    @EngineTest
    void onlyTheEnginesOwnArgumentTypesAreDescribed(Harness h) {
        assertEquals(Optional.empty(), h.provider.arguments().describe(IntegerArgumentType.integer()));
    }
}
