package g_mungus.munguscript.engine_impl.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link LiteralOfArgument#write}: a value written into a command stays one line and reads back as it was. */
class LiteralWriteTest {

    @Test
    void lineBreaksAreWrittenAsEscapes() {
        assertEquals("\"a\\nb \\\\n \\\"c\\\"\"", LiteralOfArgument.write("a\nb \\n \"c\""));
        assertEquals("\"a\\nb\"", LiteralOfArgument.write("a\r\nb"));
    }

    @Test
    void writtenTextReadsBack() throws CommandSyntaxException {
        String value = "write_page \"a\\nb\"\nset \\ (x) # y";
        Object read = CommandText.parseLiteral(StringArgumentType.string(), new StringReader(LiteralOfArgument.write(value)));
        assertEquals(value, read);
    }
}
