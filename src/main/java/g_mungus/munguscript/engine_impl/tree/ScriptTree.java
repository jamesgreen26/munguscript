package g_mungus.munguscript.engine_impl.tree;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import g_mungus.munguscript.engine_impl.argument.ValueOrLiteralArgument;
import g_mungus.munguscript.language.type.TypeKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The engine's nodes in one tree, and what their shape says about the language: which words are
 * getters and mappers, and which type a value has after each of them.
 *
 * <p>The engine makes one as it builds; a view over a tree built elsewhere finds one again by
 * {@linkplain NodeNames name}. Everything here is read from nodes, names and redirects alone, which
 * is all that survives a trip to a client.
 *
 * <pre>
 * script                     executors, if, unless
 *   executor &lt;argument&gt;     (nothing may follow a command without a condition)
 *   if, unless               -&gt; condition
 * condition                  getters that can lead to a boolean -&gt; condition/&lt;type&gt;
 * condition/&lt;type&gt;          mappers that can lead to a boolean; for boolean, the executors too
 *   executor &lt;argument&gt;     else -&gt; script
 * value                      every getter -&gt; value/&lt;type&gt;
 * value/&lt;type&gt;              every mapper from the type -&gt; value/&lt;output&gt;
 * converted/&lt;chain&gt;         the words &lt;chain&gt; holds only through a conversion: the same nodes
 * converted/&lt;chain&gt;/&lt;word&gt;  the arguments a word of &lt;chain&gt; takes only through a conversion,
 *                            after its own: the same nodes
 * </pre>
 */
public final class ScriptTree<S> {
    private final CommandNode<S> script;
    private final CommandNode<S> condition;
    private final CommandNode<S> value;
    private final Map<TypeKey, CommandNode<S>> valueChains;
    private final Map<TypeKey, CommandNode<S>> conditionChains;
    private final List<CommandNode<S>> convertedLists;
    private final Set<CommandNode<S>> converted = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * @param convertedLists the nodes that list, as their children, the words chain nodes hold only
     *                       through a conversion
     */
    public ScriptTree(CommandNode<S> script, CommandNode<S> condition, CommandNode<S> value,
                      Map<TypeKey, CommandNode<S>> valueChains, Map<TypeKey, CommandNode<S>> conditionChains,
                      Collection<CommandNode<S>> convertedLists) {
        this.script = script;
        this.condition = condition;
        this.value = value;
        this.valueChains = Map.copyOf(valueChains);
        this.conditionChains = Map.copyOf(conditionChains);
        this.convertedLists = List.copyOf(convertedLists);
        // By identity: the same word in another chain, or the type's own, is not converted.
        convertedLists.forEach(list -> converted.addAll(list.getChildren()));
    }

    /**
     * Finds the engine's nodes among the children of the node they were grafted under.
     *
     * @throws IllegalArgumentException if they are not there
     */
    public static <S> ScriptTree<S> find(CommandNode<S> graftedUnder) {
        Map<TypeKey, CommandNode<S>> valueChains = new LinkedHashMap<>();
        Map<TypeKey, CommandNode<S>> conditionChains = new LinkedHashMap<>();
        List<CommandNode<S>> convertedLists = new ArrayList<>();
        for (CommandNode<S> child : graftedUnder.getChildren()) {
            if (NodeNames.isConverted(child.getName())) {
                convertedLists.add(child);
                continue;
            }
            NodeNames.chainType(child.getName()).ifPresent(type ->
                    (NodeNames.isValueChain(child.getName()) ? valueChains : conditionChains).put(type, child));
        }
        return new ScriptTree<>(require(graftedUnder, NodeNames.SCRIPT), require(graftedUnder, NodeNames.CONDITION),
                require(graftedUnder, NodeNames.VALUE), valueChains, conditionChains, convertedLists);
    }

    private static <S> CommandNode<S> require(CommandNode<S> parent, String name) {
        CommandNode<S> child = parent.getChild(name);
        if (child == null) {
            throw new IllegalArgumentException("No script engine was grafted under '" + parent.getName()
                    + "': it has no " + name + " node");
        }
        return child;
    }

    /** Every node the engine grafts, for {@link g_mungus.munguscript.engine.ScriptEngine#graft}. */
    public List<CommandNode<S>> roots() {
        List<CommandNode<S>> roots = new ArrayList<>(List.of(script, condition, value));
        roots.addAll(valueChains.values());
        roots.addAll(conditionChains.values());
        roots.addAll(convertedLists);
        return roots;
    }

    /** Whether {@code word}, a mapper in a chain, is there only through a conversion. */
    public boolean isConverted(CommandNode<S> word) {
        return converted.contains(word);
    }

    public CommandNode<S> script() {
        return script;
    }

    public CommandNode<S> condition() {
        return condition;
    }

    public CommandNode<S> value() {
        return value;
    }

    /** The value chain nodes, by the type the value has there. */
    public Map<TypeKey, CommandNode<S>> valueChains() {
        return valueChains;
    }

    /** The type a getter gives, or empty if there is no such getter. */
    public Optional<TypeKey> getterType(String name) {
        CommandNode<S> getter = value.getChild(name);
        return getter == null ? Optional.empty() : outputOf(getter);
    }

    public boolean isGetter(String name) {
        return value.getChild(name) != null;
    }

    public boolean isMapper(String name) {
        return valueChains.values().stream().anyMatch(chain -> chain.getChild(name) != null);
    }

    /**
     * For each argument of a mapper word that has more than one, the types the arguments after it
     * take: those through a conversion, which are tried if it does not read. Empty for the rest.
     */
    public Map<ArgumentType<?>, List<TypeKey>> laterTargets() {
        Map<ArgumentType<?>, List<TypeKey>> later = new IdentityHashMap<>();
        for (CommandNode<S> chain : chainNodes()) {
            for (CommandNode<S> word : chain.getChildren()) {
                List<ArgumentCommandNode<S, ?>> arguments = argumentsOf(word);
                for (int i = 0; i < arguments.size(); i++) {
                    List<TypeKey> targets = new ArrayList<>();
                    for (ArgumentCommandNode<S, ?> after : arguments.subList(i + 1, arguments.size())) {
                        if (after.getType() instanceof ValueOrLiteralArgument slot) {
                            targets.add(slot.target());
                        }
                    }
                    if (!targets.isEmpty()) {
                        later.put(arguments.get(i).getType(), targets);
                    }
                }
            }
        }
        return later;
    }

    private List<CommandNode<S>> chainNodes() {
        List<CommandNode<S>> chains = new ArrayList<>(valueChains.values());
        chains.addAll(conditionChains.values());
        return chains;
    }

    /**
     * Whether {@code node} is an executor, after a condition or not. Told by its shape, which
     * survives being sent: an executor's argument ends the chain, a mapper's leads on.
     */
    public static boolean isExecutor(CommandNode<?> node) {
        return argumentOf(node).filter(argument -> argument.getRedirect() == null).isPresent();
    }

    /**
     * The word each argument in the tree is written after: the executor or mapper that needs it,
     * as errors name it.
     */
    public Map<ArgumentType<?>, String> argumentOwners() {
        Map<ArgumentType<?>, String> owners = new IdentityHashMap<>();
        List<CommandNode<S>> parents = new ArrayList<>(List.of(script));
        parents.addAll(valueChains.values());
        parents.addAll(conditionChains.values());
        for (CommandNode<S> parent : parents) {
            for (CommandNode<S> word : parent.getChildren()) {
                argumentsOf(word).forEach(argument -> owners.put(argument.getType(), word.getName()));
            }
        }
        return owners;
    }

    public static boolean isKeyword(String word) {
        return word.equals(NodeNames.IF) || word.equals(NodeNames.UNLESS) || word.equals(NodeNames.ELSE);
    }

    /** The type the value has once a chain reaches {@code node}, from the chain node it redirects to. */
    public static Optional<TypeKey> typeAfter(CommandNode<?> node) {
        return node.getRedirect() == null ? Optional.empty() : NodeNames.chainType(node.getRedirect().getName());
    }

    /** What a getter or mapper literal gives, through its argument if it takes one. */
    public static Optional<TypeKey> outputOf(CommandNode<?> literal) {
        Optional<TypeKey> direct = typeAfter(literal);
        return direct.isPresent() ? direct : argumentOf(literal).flatMap(ScriptTree::typeAfter);
    }

    /** What a mapper literal can give, through each of its arguments if it takes them. */
    public static List<TypeKey> outputsOf(CommandNode<?> literal) {
        Optional<TypeKey> direct = typeAfter(literal);
        return direct.isPresent() ? List.of(direct.get())
                : argumentsOf(literal).stream().flatMap(argument -> typeAfter(argument).stream()).distinct().toList();
    }

    /** Every argument written after a literal, in the order Brigadier tries them. */
    public static <S> List<ArgumentCommandNode<S, ?>> argumentsOf(CommandNode<S> literal) {
        List<ArgumentCommandNode<S, ?>> arguments = new ArrayList<>();
        for (CommandNode<S> child : literal.getChildren()) {
            if (child instanceof ArgumentCommandNode<S, ?> argument) {
                arguments.add(argument);
            }
        }
        return arguments;
    }

    /** The argument written after a mapper or executor literal, if it takes one: its own, if it has more. */
    public static <S> Optional<ArgumentCommandNode<S, ?>> argumentOf(CommandNode<S> literal) {
        for (CommandNode<S> child : literal.getChildren()) {
            if (child instanceof ArgumentCommandNode<S, ?> argument) {
                return Optional.of(argument);
            }
        }
        return Optional.empty();
    }
}
