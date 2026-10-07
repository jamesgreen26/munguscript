package g_mungus.munguscript.engine.failure;

import com.mojang.brigadier.context.StringRange;
import org.jetbrains.annotations.Nullable;

/**
 * Why a command failed, worded for players. Plain data: the host decides how to show it, whether
 * to log it and whether to keep it.
 *
 * @param playerCommand   the command as the player wrote it, before pre-processing
 * @param executedCommand the command as it ran, after pre-processing
 * @param faultRange      the part of {@code playerCommand} at fault, if it is known. A fault inside a
 *                        {@code value_of(...)} or an alias is still reported here, in the player's
 *                        own text.
 */
public record ScriptFailure(
        String reason,
        String playerCommand,
        String executedCommand,
        @Nullable StringRange faultRange
) {

    /** The text at fault, or null when the failure has no position. */
    public @Nullable String faultText() {
        return faultRange == null ? null : faultRange.get(playerCommand);
    }
}
