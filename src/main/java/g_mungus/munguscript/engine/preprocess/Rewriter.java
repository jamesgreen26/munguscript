package g_mungus.munguscript.engine.preprocess;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a rewritten text piece by piece from an input, keeping track of where each piece came
 * from. Pieces are appended in input order and must cover the input with no gaps.
 */
public final class Rewriter {
    private final String input;
    private final StringBuilder output = new StringBuilder();
    private final List<Segment> segments = new ArrayList<>();
    private boolean changed;

    public Rewriter(String input) {
        this.input = input;
    }

    /** Copies {@code input[start, end)} as it is. */
    public Rewriter keep(int start, int end) {
        if (start == end) return this;
        Segment last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
        if (last != null && last.kind == Kind.KEEP && last.inEnd == start) {
            segments.set(segments.size() - 1,
                    new Segment(Kind.KEEP, last.outStart, last.outEnd + (end - start), last.inStart, end, null));
        } else {
            segments.add(new Segment(Kind.KEEP, output.length(), output.length() + (end - start), start, end, null));
        }
        output.append(input, start, end);
        return this;
    }

    /** Puts {@code text} in place of {@code input[start, end)}. Any part of it maps to the whole of the original. */
    public Rewriter replace(int start, int end, String text) {
        if (text.equals(input.substring(start, end))) {
            return keep(start, end);
        }
        segments.add(new Segment(Kind.REPLACE, output.length(), output.length() + text.length(), start, end, null));
        output.append(text);
        changed = true;
        return this;
    }

    /** Puts a rewrite of {@code input[start, end)} in its place, keeping that rewrite's own map. */
    public Rewriter rewrite(int start, int end, Rewritten rewritten) {
        if (rewritten.map() == SourceMap.IDENTITY && rewritten.text().equals(input.substring(start, end))) {
            return keep(start, end);
        }
        segments.add(new Segment(Kind.NESTED, output.length(), output.length() + rewritten.text().length(),
                start, end, rewritten.map()));
        output.append(rewritten.text());
        changed = true;
        return this;
    }

    public Rewritten build() {
        if (!changed) {
            return Rewritten.unchanged(output.toString());
        }
        return new Rewritten(output.toString(), new Segments(List.copyOf(segments), output.length(), input.length()));
    }

    private enum Kind { KEEP, REPLACE, NESTED }

    private record Segment(Kind kind, int outStart, int outEnd, int inStart, int inEnd, SourceMap nested) {
        int start(int position) {
            return switch (kind) {
                case KEEP -> inStart + (position - outStart);
                case REPLACE -> inStart;
                case NESTED -> inStart + nested.originalStart(position - outStart);
            };
        }

        int end(int position) {
            return switch (kind) {
                case KEEP -> inStart + (position - outStart);
                case REPLACE -> inEnd;
                case NESTED -> inStart + nested.originalEnd(position - outStart);
            };
        }
    }

    private record Segments(List<Segment> segments, int outLength, int inLength) implements SourceMap {
        @Override
        public int originalStart(int position) {
            int p = Math.max(0, Math.min(outLength, position));
            for (Segment segment : segments) {
                if (segment.outStart <= p && p < segment.outEnd) return segment.start(p);
            }
            // At the very end, or where a piece was replaced with nothing.
            for (Segment segment : segments) {
                if (segment.outStart == p) return segment.inStart;
            }
            return inLength;
        }

        @Override
        public int originalEnd(int position) {
            int p = Math.max(0, Math.min(outLength, position));
            for (Segment segment : segments) {
                if (segment.outStart < p && p <= segment.outEnd) return segment.end(p);
            }
            for (int i = segments.size() - 1; i >= 0; i--) {
                if (segments.get(i).outEnd == p) return segments.get(i).inEnd;
            }
            return 0;
        }
    }
}
