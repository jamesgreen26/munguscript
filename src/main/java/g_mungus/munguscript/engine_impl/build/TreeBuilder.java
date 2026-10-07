package g_mungus.munguscript.engine_impl.build;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine_impl.argument.ArgumentLookup;
import g_mungus.munguscript.engine_impl.argument.OverloadedArgument;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.engine_impl.run.NodeActions;
import g_mungus.munguscript.engine_impl.run.Step;
import g_mungus.munguscript.engine_impl.tree.NodeNames;
import g_mungus.munguscript.engine_impl.tree.ScriptArgumentNode;
import g_mungus.munguscript.engine_impl.tree.ScriptLiteralNode;
import g_mungus.munguscript.engine_impl.tree.ScriptTree;
import g_mungus.munguscript.engine_impl.tree.TypeGraph;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptArgumentMapper;
import g_mungus.munguscript.language.node.ScriptGetter;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.type.BuildEnvironment;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the Brigadier nodes for one build's registrations, in the shape {@link ScriptTree}
 * describes.
 *
 * <p>A chain is a node per type, holding the mappers from that type; each getter and mapper
 * redirects to the chain of the type it gives, so a chain of any length is a walk through a fixed
 * number of nodes. Conditions and {@code value_of} use separate chains because after a condition's
 * boolean come the executors, and inside a {@code value_of} nothing does.
 */
public final class TreeBuilder<S> {
    private static final TypeKey BOOLEAN = BuiltInTypes.BOOLEAN.key();

    private final Registrations registrations;
    private final BuildEnvironment environment;
    private final NodeActions<S> actions;
    private final ArgumentLookup lookup;
    private final TypeGraph graph;

    public TreeBuilder(Registrations registrations, BuildEnvironment environment, NodeActions<S> actions,
                       ArgumentLookup lookup) {
        this.registrations = registrations;
        this.environment = environment;
        this.actions = actions;
        this.lookup = lookup;
        this.graph = TypeGraph.of(registrations.mappers().stream()
                .map(mapper -> new TypeGraph.Edge(input(mapper), output(mapper))).toList());
    }

    public ScriptTree<S> build() {
        CommandNode<S> script = ScriptLiteralNode.place(NodeNames.SCRIPT);
        CommandNode<S> condition = ScriptLiteralNode.place(NodeNames.CONDITION);
        CommandNode<S> value = ScriptLiteralNode.place(NodeNames.VALUE);
        Map<TypeKey, CommandNode<S>> valueChains = new LinkedHashMap<>();
        Map<TypeKey, CommandNode<S>> conditionChains = new LinkedHashMap<>();
        for (ScriptType<?> type : registrations.types()) {
            valueChains.put(type.key(), ScriptLiteralNode.place(NodeNames.valueChain(type.key())));
            // A condition only goes through types it can still get to a boolean from.
            if (graph.reaches(type.key(), BOOLEAN)) {
                conditionChains.put(type.key(), ScriptLiteralNode.place(NodeNames.conditionChain(type.key())));
            }
        }
        Chain valueChain = new Chain(valueChains, actions::lastValueStep, actions::valueStep);
        Chain conditionChain = new Chain(conditionChains, step -> null, actions::conditionStep);

        for (ScriptGetter<?> getter : registrations.getters()) {
            value.addChild(valueChain.step(getter));
            if (conditionChains.containsKey(getter.outputType().key())) {
                condition.addChild(conditionChain.step(getter));
            }
        }
        for (ScriptNode mapper : registrations.mappers()) {
            valueChains.get(input(mapper)).addChild(valueChain.step(mapper));
            if (conditionChains.containsKey(output(mapper))) {
                conditionChains.get(input(mapper)).addChild(conditionChain.step(mapper));
            }
        }

        // Each executor is built twice. Under the script root it ends the command; after a
        // condition it may be followed by its own else, which runs it and leads back to the script
        // root, so else can only follow a branch that has a condition, and the last branch of a
        // chain ends it.
        CommandNode<S> afterCondition = conditionChains.get(BOOLEAN);
        for (ExecutorGroup group : registrations.executors()) {
            OverloadedArgument argument = new OverloadedArgument(group.variants().stream()
                    .map(executor -> slot(executor.argumentType(environment), executor.inputType().key()))
                    .toList(), lookup, true);
            Command<S> run = actions.executor(group);
            script.addChild(executor(group, argument, run, null));
            // A boolean mapper of the same name wins after a condition, since Brigadier would
            // otherwise merge the two.
            if (afterCondition.getChild(group.name()) == null) {
                CommandNode<S> elseNode = new ScriptLiteralNode<>(NodeNames.ELSE, null, script, actions.elseBranch(run));
                afterCondition.addChild(executor(group, argument, run, elseNode));
            }
        }
        script.addChild(new ScriptLiteralNode<>(NodeNames.IF, null, condition, actions.startCondition(false)));
        script.addChild(new ScriptLiteralNode<>(NodeNames.UNLESS, null, condition, actions.startCondition(true)));
        return new ScriptTree<>(script, condition, value, valueChains, conditionChains);
    }

    /** {@code name <argument>}, where the argument runs the overload, followed by {@code else} if given. */
    private CommandNode<S> executor(ExecutorGroup group, OverloadedArgument type, Command<S> run,
                                    @Nullable CommandNode<S> elseNode) {
        CommandNode<S> argument = new ScriptArgumentNode<>(group.argumentName(), type, run, null, null);
        if (elseNode != null) {
            argument.addChild(elseNode);
        }
        CommandNode<S> literal = new ScriptLiteralNode<>(group.name(), null, null, null);
        literal.addChild(argument);
        return literal;
    }

    private ValueOrLiteralArgument slot(ArgumentType<?> literal, TypeKey target) {
        return new ValueOrLiteralArgument(literal, target, lookup);
    }

    /**
     * How the nodes of one kind of chain are made: which chain nodes they redirect to, and what they
     * do when they are the last step and when more follow.
     */
    private final class Chain {
        private final Map<TypeKey, CommandNode<S>> chains;
        private final Function<Step<S>, @Nullable Command<S>> last;
        private final Function<Step<S>, RedirectModifier<S>> more;

        Chain(Map<TypeKey, CommandNode<S>> chains, Function<Step<S>, @Nullable Command<S>> last,
              Function<Step<S>, RedirectModifier<S>> more) {
            this.chains = chains;
            this.last = last;
            this.more = more;
        }

        /** A getter or mapper literal, with its argument if it takes one, leading on to its output's chain. */
        CommandNode<S> step(ScriptNode node) {
            CommandNode<S> next = chains.get(output(node));
            if (!(node instanceof ScriptArgumentMapper<?, ?, ?> mapper)) {
                Step<S> step = actions.step(node, null);
                return new ScriptLiteralNode<>(node.displayName(), last.apply(step), next, more.apply(step));
            }
            Step<S> step = actions.step(node, mapper.argumentHint());
            ArgumentType<?> type = mapper.argumentScriptType() == null
                    ? mapper.argumentType(environment)
                    : slot(mapper.argumentType(environment), mapper.argumentScriptType().key());
            CommandNode<S> literal = new ScriptLiteralNode<>(node.displayName(), null, null, null);
            literal.addChild(new ScriptArgumentNode<>(mapper.argumentHint(), type, last.apply(step), next,
                    more.apply(step)));
            return literal;
        }
    }

    private static TypeKey input(ScriptNode node) {
        return NodeTypes.input(node).orElseThrow().key();
    }

    private static TypeKey output(ScriptNode node) {
        return NodeTypes.output(node).orElseThrow().key();
    }
}
