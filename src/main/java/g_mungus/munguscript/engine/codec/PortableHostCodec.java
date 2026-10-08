package g_mungus.munguscript.engine.codec;

import com.mojang.brigadier.arguments.ArgumentType;
import g_mungus.munguscript.language.node.Applicability;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link HostCodec} for clients that do not have the host's code, such as an editor: it writes
 * each argument type as its {@link ArgumentShape} and examples, and reads it back as an argument of
 * that shape. Applicabilities are written as their text and read back as a {@link Described}, which
 * only names them.
 *
 * <p>What it reads parses and suggests by shape, so a client sees the host's language as closely
 * as the shapes describe it: exactly for Brigadier's own argument types and for a
 * {@link PortableArgument}, loosely for anything else.
 */
public final class PortableHostCodec implements HostCodec {
    private static final byte BOOL = 0;
    private static final byte INT = 1;
    private static final byte LONG = 2;
    private static final byte FLOAT = 3;
    private static final byte DOUBLE = 4;
    private static final byte TEXT = 5;
    private static final byte WORD = 6;
    private static final byte ONE_OF = 7;
    private static final byte SEQUENCE = 8;
    private static final byte LOOSE = 9;

    /** How deep sequences may nest in what is read, so a bad file cannot recurse without end. */
    private static final int MAX_DEPTH = 16;

    /** An applicability a client only knows by its text. */
    public record Described(String text) implements Applicability {
    }

    @Override
    public void writeArgumentType(DataOutput out, ArgumentType<?> type) throws IOException {
        writeShape(out, ArgumentShape.of(type));
        List<String> examples = List.copyOf(type.getExamples());
        out.writeInt(examples.size());
        for (String example : examples) {
            out.writeUTF(example);
        }
    }

    @Override
    public ArgumentType<?> readArgumentType(DataInput in) throws IOException {
        ArgumentShape shape = readShape(in, 0);
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative count of examples: " + count);
        }
        List<String> examples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            examples.add(in.readUTF());
        }
        return shape.argumentType(examples);
    }

    @Override
    public void writeApplicability(DataOutput out, Applicability applicability) throws IOException {
        out.writeUTF(applicability instanceof Described described ? described.text() : applicability.toString());
    }

    @Override
    public Applicability readApplicability(DataInput in) throws IOException {
        return new Described(in.readUTF());
    }

    private static void writeShape(DataOutput out, ArgumentShape shape) throws IOException {
        if (shape instanceof ArgumentShape.Bool) {
            out.writeByte(BOOL);
        } else if (shape instanceof ArgumentShape.IntRange range) {
            out.writeByte(INT);
            out.writeInt(range.min());
            out.writeInt(range.max());
        } else if (shape instanceof ArgumentShape.LongRange range) {
            out.writeByte(LONG);
            out.writeLong(range.min());
            out.writeLong(range.max());
        } else if (shape instanceof ArgumentShape.FloatRange range) {
            out.writeByte(FLOAT);
            out.writeFloat(range.min());
            out.writeFloat(range.max());
        } else if (shape instanceof ArgumentShape.DoubleRange range) {
            out.writeByte(DOUBLE);
            out.writeDouble(range.min());
            out.writeDouble(range.max());
        } else if (shape instanceof ArgumentShape.Text text) {
            out.writeByte(TEXT);
            out.writeUTF(text.kind().name());
        } else if (shape instanceof ArgumentShape.Word word) {
            out.writeByte(WORD);
            out.writeUTF(word.word());
        } else if (shape instanceof ArgumentShape.OneOf oneOf) {
            out.writeByte(ONE_OF);
            out.writeBoolean(oneOf.ignoreCase());
            out.writeInt(oneOf.words().size());
            for (String word : oneOf.words()) {
                out.writeUTF(word);
            }
        } else if (shape instanceof ArgumentShape.Sequence sequence) {
            out.writeByte(SEQUENCE);
            out.writeInt(sequence.parts().size());
            for (ArgumentShape part : sequence.parts()) {
                writeShape(out, part);
            }
        } else {
            out.writeByte(LOOSE);
        }
    }

    private static ArgumentShape readShape(DataInput in, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Argument shapes nest deeper than " + MAX_DEPTH);
        }
        byte form = in.readByte();
        switch (form) {
            case BOOL:
                return new ArgumentShape.Bool();
            case INT:
                return new ArgumentShape.IntRange(in.readInt(), in.readInt());
            case LONG:
                return new ArgumentShape.LongRange(in.readLong(), in.readLong());
            case FLOAT:
                return new ArgumentShape.FloatRange(in.readFloat(), in.readFloat());
            case DOUBLE:
                return new ArgumentShape.DoubleRange(in.readDouble(), in.readDouble());
            case TEXT: {
                String kind = in.readUTF();
                try {
                    return new ArgumentShape.Text(ArgumentShape.Text.Kind.valueOf(kind));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Unknown kind of text '" + kind + "'", e);
                }
            }
            case WORD:
                return new ArgumentShape.Word(in.readUTF());
            case ONE_OF: {
                boolean ignoreCase = in.readBoolean();
                List<String> words = new ArrayList<>();
                for (int i = count(in, "words"); i > 0; i--) {
                    words.add(in.readUTF());
                }
                return new ArgumentShape.OneOf(words, ignoreCase);
            }
            case SEQUENCE: {
                List<ArgumentShape> parts = new ArrayList<>();
                for (int i = count(in, "sequence parts"); i > 0; i--) {
                    parts.add(readShape(in, depth + 1));
                }
                if (parts.isEmpty()) {
                    throw new IOException("A sequence has no parts");
                }
                return new ArgumentShape.Sequence(parts);
            }
            case LOOSE:
                return new ArgumentShape.Loose();
            default:
                throw new IOException("Unknown argument shape " + form);
        }
    }

    private static int count(DataInput in, String what) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative count of " + what + ": " + count);
        }
        return count;
    }
}
