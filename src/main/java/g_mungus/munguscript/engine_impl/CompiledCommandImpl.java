package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.CompiledCommand;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine_impl.run.CommandRun;
import g_mungus.munguscript.engine_impl.run.Parses;

/** A command parsed once, run again from its parse with a new source each time, as are the {@code value_of}s in it. */
final class CompiledCommandImpl<S> implements CompiledCommand<S> {
    private final ScriptHost<S> host;
    private final CommandDispatcher<S> dispatcher;
    private final ParseResults<S> parse;
    private final Parses parses = new Parses();

    CompiledCommandImpl(ScriptHost<S> host, CommandDispatcher<S> dispatcher, ParseResults<S> parse) {
        this.host = host;
        this.dispatcher = dispatcher;
        this.parse = parse;
    }

    @Override
    public String command() {
        return parse.getReader().getString();
    }

    @Override
    public int run(S source) throws CommandSyntaxException {
        return dispatcher.execute(Parses.withSource(parse, host.withRunState(source, new CommandRun(null, parses))));
    }
}
