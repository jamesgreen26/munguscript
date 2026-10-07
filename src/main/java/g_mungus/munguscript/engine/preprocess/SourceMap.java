package g_mungus.munguscript.engine.preprocess;

import com.mojang.brigadier.context.StringRange;

/**
 * Connects rewritten text to the text it came from, so that errors point at what the player
 * wrote. A range inside an expansion maps to the whole token that was expanded.
 *
 * <p>The start and end of a range are placed separately, which is what lets maps be combined: a
 * position inside a replaced word starts at the word's start and ends at its end.
 */
public interface SourceMap {

    SourceMap IDENTITY = new SourceMap() {
        @Override
        public int originalStart(int position) {
            return position;
        }

        @Override
        public int originalEnd(int position) {
            return position;
        }
    };

    /** Where a range starting at {@code position} starts in the original. */
    int originalStart(int position);

    /** Where a range ending at {@code position} ends in the original. */
    int originalEnd(int position);

    default StringRange toOriginal(StringRange rewritten) {
        int start = originalStart(rewritten.getStart());
        int end = originalEnd(rewritten.getEnd());
        if (rewritten.isEmpty()) {
            // An empty range inside a replaced word still points at the whole word.
            end = Math.max(end, originalEnd(rewritten.getStart()));
        }
        return StringRange.between(start, Math.max(start, end));
    }

    /**
     * Two rewrites one after the other.
     *
     * @param earlier maps the middle text to the original
     * @param later   maps the final text to the middle one
     */
    static SourceMap compose(SourceMap earlier, SourceMap later) {
        if (earlier == IDENTITY) return later;
        if (later == IDENTITY) return earlier;
        return new SourceMap() {
            @Override
            public int originalStart(int position) {
                return earlier.originalStart(later.originalStart(position));
            }

            @Override
            public int originalEnd(int position) {
                return earlier.originalEnd(later.originalEnd(position));
            }
        };
    }
}
