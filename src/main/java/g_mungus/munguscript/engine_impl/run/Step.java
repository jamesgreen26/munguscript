package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import g_mungus.munguscript.engine.ScriptHost;
import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptExecutor;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptMapper;
import g_mungus.munguscript.language.node.ScriptNode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * One getter or mapper in a chain, as a condition or a {@code value_of} runs it: the value so far
 * goes in, the next comes out. Whatever the node's function throws becomes a failure that names the
 * node and points at it.
 *
 * <p>A mapper of a type the value is only usable as converts the value first.
 */
public final class Step<S> {
    private final ScriptNode node;
    private final @Nullable String argumentName;
    private final ScriptHost<S> host;
    private final Slots<S> slots;
    private final Function<@Nullable Object, @Nullable Object> convert;

    /**
     * @param argumentName the name of the argument node after an argument mapper; null otherwise
     * @param convert      turns the value so far into the mapper's input type
     */
    Step(ScriptNode node, @Nullable String argumentName, ScriptHost<S> host, Slots<S> slots,
         Function<@Nullable Object, @Nullable Object> convert) {
        if (node instanceof ScriptExecutor<?, ?>) {
            throw new IllegalArgumentException("An executor is not a step in a chain");
        }
        this.node = node;
        this.argumentName = argumentName;
        this.host = host;
        this.slots = slots;
        this.convert = convert;
    }

    @Nullable Object apply(@Nullable Object input, CommandContext<S> context) {
        NodeContext nodeContext = NodeContext.of(host, context.getSource());
        try {
            if (node instanceof ScriptGetter<?> getter) {
                return getter.get(nodeContext);
            } else if (node instanceof ScriptMapper<?, ?> mapper) {
                return map(mapper, convert.apply(input), nodeContext);
            } else if (node instanceof ScriptArgumentMapper<?, ?, ?> mapper) {
                return map(mapper, convert.apply(input), nodeContext, context);
            }
            throw new IllegalStateException("unreachable");
        } catch (ScriptFault fault) {
            // Already explained, by a value_of in the argument.
            throw fault;
        } catch (RuntimeException e) {
            // The range of the node that ran: the mapper's argument if it has one, which is usually
            // what made it fail.
            List<ParsedCommandNode<S>> nodes = context.getNodes();
            throw new ScriptFault("Could not evaluate '" + node.displayName() + "': " + Reasons.of(e),
                    context.getInput(), nodes.get(nodes.size() - 1).getRange(), e);
        }
    }

    // The tree only joins nodes whose types line up, so the input is always of the input type.
    @SuppressWarnings("unchecked")
    private static <I> Object map(ScriptMapper<I, ?> mapper, @Nullable Object input, NodeContext context) {
        return mapper.map((I) input, context);
    }

    @SuppressWarnings("unchecked")
    private <I, A> Object map(ScriptArgumentMapper<I, ?, A> mapper, @Nullable Object input, NodeContext nodeContext,
                              CommandContext<S> context) {
        Object parsed = context.getArgument(argumentName, Object.class);
        Object written = mapper.argumentScriptType() == null ? parsed
                : slots.written(parsed, mapper.argumentScriptType().key(), mapper.displayName(), context);
        NodeContext withArgument = nodeContext.withArgument(mapper.resolveArgument(written, nodeContext));
        return mapper.map((I) input, withArgument);
    }
}
