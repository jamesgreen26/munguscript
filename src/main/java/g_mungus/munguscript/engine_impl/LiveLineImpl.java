package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine_impl.run.CommandRun;
import g_mungus.munguscript.engine_impl.run.LivePass;
import g_mungus.munguscript.engine_impl.run.Parses;
import g_mungus.munguscript.language.node.ScriptNode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A command parsed once, run again from its parse with a new source each pass, as are the
 * {@code value_of}s in it ({@link Parses}).
 */
final class LiveLineImpl<S> implements LiveLine<S> {
    private final ScriptHost<S> host;
    private final CommandDispatcher<S> dispatcher;
    private final ParseResults<S> parse;
    private final Set<ScriptNode> live;
    private final Parses parses = new Parses();
    private volatile LivePass.@Nullable Recorded recorded;

    LiveLineImpl(ScriptHost<S> host, CommandDispatcher<S> dispatcher, ParseResults<S> parse, Set<ScriptNode> live) {
        this.host = host;
        this.dispatcher = dispatcher;
        this.parse = parse;
        this.live = live;
    }

    @Override
    public String command() {
        return parse.getReader().getString();
    }

    @Override
    public void refresh(S source) throws CommandSyntaxException {
        LivePass pass = LivePass.refresh(live, parses);
        execute(source, pass);
        recorded = pass.recorded();
    }

    @Override
    public int run(S source) throws CommandSyntaxException {
        LivePass.Recorded last = recorded;
        if (last == null) {
            throw new IllegalStateException("A live line must be refreshed before it runs");
        }
        return execute(source, LivePass.live(live, last, parses));
    }

    private int execute(S source, LivePass pass) throws CommandSyntaxException {
        return dispatcher.execute(Parses.withSource(parse, host.withRunState(source, new CommandRun(pass))));
    }
}
