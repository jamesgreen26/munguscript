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
import g_mungus.munguscript.engine_impl.tree.Conversions;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p>A type usable as others also holds their mappers, which convert the value before they map it.
 */
public final class TreeBuilder<S> {
    private static final TypeKey BOOLEAN = BuiltInTypes.BOOLEAN.key();

    private final Registrations registrations;
    private final BuildEnvironment environment;
    private final NodeActions<S> actions;
    private final ArgumentLookup lookup;
    private final Conversions conversions;
    private final TypeGraph graph;

    public TreeBuilder(Registrations registrations, BuildEnvironment environment, NodeActions<S> actions,
                       ArgumentLookup lookup) {
        this.registrations = registrations;
        this.environment = environment;
        this.actions = actions;
        this.lookup = lookup;
        this.conversions = Conversions.of(registrations.types());
        this.graph = TypeGraph.of(registrations.mappers().stream()
                .map(mapper -> new TypeGraph.Edge(input(mapper), output(mapper))).toList())
                .with(conversions.edges());
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
        inheritMappers(valueChain, conditionChain);
        script.addChild(new ScriptLiteralNode<>(NodeNames.IF, null, condition, actions.startCondition(false)));
        script.addChild(new ScriptLiteralNode<>(NodeNames.UNLESS, null, condition, actions.startCondition(true)));
        List<CommandNode<S>> convertedLists = new ArrayList<>(valueChain.converted.values());
        convertedLists.addAll(conditionChain.converted.values());
        return new ScriptTree<>(script, condition, value, valueChains, conditionChains, convertedLists);
    }

    /**
     * Gives each type's chains the mappers of the types it is usable as, nearest first, where the
     * chain has no word of the same name. Done after the executors, so that after a condition an
     * executor wins over a mapper the boolean only takes from a type it is usable as.
     */
    private void inheritMappers(Chain valueChain, Chain conditionChain) {
        Map<TypeKey, List<ScriptNode>> mappersFrom = new LinkedHashMap<>();
        for (ScriptNode mapper : registrations.mappers()) {
            mappersFrom.computeIfAbsent(input(mapper), key -> new ArrayList<>()).add(mapper);
        }
        for (ScriptType<?> type : registrations.types()) {
            TypeKey from = type.key();
            for (TypeKey as : conversions.usableAs(from)) {
                Function<@Nullable Object, @Nullable Object> convert = value -> conversions.convert(value, from, as);
                for (ScriptNode mapper : mappersFrom.getOrDefault(as, List.of())) {
                    valueChain.inherit(from, mapper, convert);
                    if (conditionChain.chains.containsKey(output(mapper))) {
                        conditionChain.inherit(from, mapper, convert);
                    }
                }
            }
        }
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
        /** For each chain node, the node listing what it holds only through a conversion. */
        private final Map<CommandNode<S>, CommandNode<S>> converted = new LinkedHashMap<>();

        Chain(Map<TypeKey, CommandNode<S>> chains, Function<Step<S>, @Nullable Command<S>> last,
              Function<Step<S>, RedirectModifier<S>> more) {
            this.chains = chains;
            this.last = last;
            this.more = more;
        }

        /** A getter or mapper literal, with its argument if it takes one, leading on to its output's chain. */
        CommandNode<S> step(ScriptNode node) {
            return step(node, Function.identity(), false);
        }

        /** {@code mapper} in the chain of {@code type}, if there is one and it has no word of that name yet. */
        void inherit(TypeKey type, ScriptNode mapper, Function<@Nullable Object, @Nullable Object> convert) {
            CommandNode<S> chain = chains.get(type);
            if (chain != null && chain.getChild(mapper.displayName()) == null) {
                CommandNode<S> word = step(mapper, convert, true);
                chain.addChild(word);
                converted.computeIfAbsent(chain, node -> ScriptLiteralNode.place(NodeNames.converted(node.getName())))
                        .addChild(word);
            }
        }

        /** @param converted whether the chain holds it only through a conversion */
        private CommandNode<S> step(ScriptNode node, Function<@Nullable Object, @Nullable Object> convert,
                                    boolean converted) {
            CommandNode<S> next = chains.get(output(node));
            if (!(node instanceof ScriptArgumentMapper<?, ?, ?> mapper)) {
                Step<S> step = actions.step(node, null, convert);
                return new ScriptLiteralNode<>(node.displayName(), last.apply(step), next, more.apply(step), converted);
            }
            Step<S> step = actions.step(node, mapper.argumentHint(), convert);
            ArgumentType<?> type = mapper.argumentScriptType() == null
                    ? mapper.argumentType(environment)
                    : slot(mapper.argumentType(environment), mapper.argumentScriptType().key());
            CommandNode<S> literal = new ScriptLiteralNode<>(node.displayName(), null, null, null, converted);
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
