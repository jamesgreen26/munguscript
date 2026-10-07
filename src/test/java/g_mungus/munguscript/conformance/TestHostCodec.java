package g_mungus.munguscript.conformance;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.munguscript.engine.codec.HostCodec;
import g_mungus.munguscript.language.node.Applicability;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes the argument types and applicabilities the tests use as bytes, the way a host would with
 * its own registries, and counts what it writes and reads.
 */
final class TestHostCodec implements HostCodec {
    int argumentsWritten;
    int argumentsRead;
    int applicabilitiesWritten;
    int applicabilitiesRead;

    @Override
    public void writeArgumentType(DataOutput out, ArgumentType<?> type) throws IOException {
        argumentsWritten++;
        if (type instanceof IntegerArgumentType integer) {
            out.writeUTF("int");
            out.writeInt(integer.getMinimum());
            out.writeInt(integer.getMaximum());
        } else if (type instanceof DoubleArgumentType real) {
            out.writeUTF("double");
            out.writeDouble(real.getMinimum());
            out.writeDouble(real.getMaximum());
        } else if (type instanceof StringArgumentType string) {
            out.writeUTF("string");
            out.writeUTF(string.getType().name());
        } else if (type instanceof BoolArgumentType) {
            out.writeUTF("bool");
        } else if (type instanceof TestTypes.PointArgument) {
            out.writeUTF("point");
        } else if (type instanceof TestTypes.ColorArgument) {
            out.writeUTF("color");
        } else {
            throw new IllegalArgumentException("The test host cannot write " + type);
        }
    }

    @Override
    public ArgumentType<?> readArgumentType(DataInput in) throws IOException {
        argumentsRead++;
        String kind = in.readUTF();
        return switch (kind) {
            case "int" -> IntegerArgumentType.integer(in.readInt(), in.readInt());
            case "double" -> DoubleArgumentType.doubleArg(in.readDouble(), in.readDouble());
            case "string" -> switch (StringArgumentType.StringType.valueOf(in.readUTF())) {
                case SINGLE_WORD -> StringArgumentType.word();
                case QUOTABLE_PHRASE -> StringArgumentType.string();
                case GREEDY_PHRASE -> StringArgumentType.greedyString();
            };
            case "bool" -> BoolArgumentType.bool();
            case "point" -> new TestTypes.PointArgument();
            case "color" -> new TestTypes.ColorArgument();
            default -> throw new IOException("Unknown argument type '" + kind + "'");
        };
    }

    @Override
    public void writeApplicability(DataOutput out, Applicability applicability) throws IOException {
        applicabilitiesWritten++;
        Set<String> ids = new TreeSet<>(((TestHost.Blocks) applicability).ids());
        out.writeInt(ids.size());
        for (String id : ids) {
            out.writeUTF(id);
        }
    }

    @Override
    public Applicability readApplicability(DataInput in) throws IOException {
        applicabilitiesRead++;
        int count = in.readInt();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(in.readUTF());
        }
        return new TestHost.Blocks(Set.copyOf(ids));
    }
}
