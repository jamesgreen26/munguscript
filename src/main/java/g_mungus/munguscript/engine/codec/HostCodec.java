package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.node.Applicability;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * How a host writes and reads its own objects inside an encoded script tree. Everything else in
 * the tree is the library's to encode ({@link ScriptTreeCodec}).
 *
 * <p>Argument types here are those the engine does not own: the argument each type's literal form
 * is read with (Brigadier's own, such as {@code IntegerArgumentType}, as well as the host's), and
 * the raw arguments of mappers made with {@code ScriptNodes.rawArgumentMapper}. Each read must
 * consume exactly what the matching write wrote.
 */
public interface HostCodec {

    void writeArgumentType(DataOutput out, ArgumentType<?> type) throws IOException;

    ArgumentType<?> readArgumentType(DataInput in) throws IOException;

    void writeApplicability(DataOutput out, Applicability applicability) throws IOException;

    Applicability readApplicability(DataInput in) throws IOException;
}
