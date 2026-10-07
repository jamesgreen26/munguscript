package g_mungus.munguscript.engine.preprocess;

/** Text produced by a rewrite, with the map back to what it was made from. */
public record Rewritten(String text, SourceMap map) {

    public static Rewritten unchanged(String text) {
        return new Rewritten(text, SourceMap.IDENTITY);
    }

    /** This rewrite followed by another of its output. */
    public Rewritten then(Rewritten next) {
        return new Rewritten(next.text, SourceMap.compose(map, next.map));
    }
}
