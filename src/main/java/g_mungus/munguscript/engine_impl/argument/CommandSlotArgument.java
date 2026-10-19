package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The slot of a command executor: a {@code command(...)}, whose inside is read through the view as a
 * whole command while it parses, so one that is not, or one with an executor not meant for inside it, is
 * a syntax error wherever it stands.
 */
public final class CommandSlotArgument implements ArgumentType<WrappedCommand> {
    /** What the slot's node is called. */
    public static final String NAME = "command";

    private final ArgumentLookup lookup;

    public CommandSlotArgument(ArgumentLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public WrappedCommand parse(StringReader reader) throws CommandSyntaxException {
        WrappedCommand wrapped = WrappedCommand.read(reader);
        lookup.view().checkCommand(this, wrapped);
        return wrapped;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return lookup.view().suggest(this, context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("command(...)");
    }

    @Override
    public String toString() {
        return "command_slot";
    }
}
