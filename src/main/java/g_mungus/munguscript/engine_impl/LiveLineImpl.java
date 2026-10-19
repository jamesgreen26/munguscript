package g_mungus.munguscript.engine_impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.munguscript.engine.LiveLine;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.engine_impl.run.CommandRun;
import g_mungus.munguscript.engine_impl.run.LivePass;
import g_mungus.munguscript.language.node.ScriptNode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A command parsed once, run again from its parse with a new source each pass. Brigadier only
 * uses the source of the parse's root context; every later stage gets the source its redirect
 * gives, so replacing the root's is enough.
 */
final class LiveLineImpl<S> implements LiveLine<S> {
    private final ScriptHost<S> host;
    private final CommandDispatcher<S> dispatcher;
    private final ParseResults<S> parse;
    private final Set<ScriptNode> live;
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
        LivePass pass = LivePass.refresh(live);
        execute(source, pass);
        recorded = pass.recorded();
    }

    @Override
    public int run(S source) throws CommandSyntaxException {
        LivePass.Recorded last = recorded;
        if (last == null) {
            throw new IllegalStateException("A live line must be refreshed before it runs");
        }
        return execute(source, LivePass.live(live, last));
    }

    private int execute(S source, LivePass pass) throws CommandSyntaxException {
        CommandContextBuilder<S> context = withSource(parse.getContext(), host.withRunState(source, new CommandRun(pass)));
        return dispatcher.execute(new ParseResults<>(context, parse.getReader(), parse.getExceptions()));
    }

    /**
     * The root context again, with {@code source}. Not {@link CommandContextBuilder#copy}, which
     * leaves out the redirect modifier, so a command starting with {@code if} would never start its
     * condition.
     */
    private static <S> CommandContextBuilder<S> withSource(CommandContextBuilder<S> parsed, S source) {
        CommandContextBuilder<S> context = new CommandContextBuilder<>(parsed.getDispatcher(), source,
                parsed.getRootNode(), parsed.getRange().getStart());
        for (ParsedCommandNode<S> node : parsed.getNodes()) {
            context.withNode(node.getNode(), node.getRange());
        }
        parsed.getArguments().forEach(context::withArgument);
        context.withCommand(parsed.getCommand());
        if (parsed.getChild() != null) {
            context.withChild(parsed.getChild());
        }
        return context;
    }
}
