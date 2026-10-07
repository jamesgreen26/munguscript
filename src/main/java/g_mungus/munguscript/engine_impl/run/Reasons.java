package g_mungus.munguscript.engine_impl.run;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Turning whatever a node threw into words for the script's author, never an exception name. */
public final class Reasons {
    public static final String UNKNOWN = "The command failed for an unknown reason";

    private Reasons() {
    }

    /**
     * The first message in the cause chain that someone wrote. Wrappers such as
     * {@code new RuntimeException(cause)} take the cause's {@code toString()} as their message,
     * which names the exception class, so those are skipped in favour of the cause.
     */
    public static String of(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current instanceof CommandSyntaxException syntax
                    ? syntax.getRawMessage().getString() : current.getMessage();
            boolean generated = current.getCause() != null && current.getCause().toString().equals(message);
            if (message != null && !message.isBlank() && !generated) {
                return message;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return UNKNOWN;
    }
}
