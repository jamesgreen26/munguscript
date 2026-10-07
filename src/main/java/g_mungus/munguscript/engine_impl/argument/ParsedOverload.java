package g_mungus.munguscript.engine_impl.argument;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an overloaded executor's argument parsed to: for each variant that read the most of the
 * command, what it read. Which of them runs is decided when the command runs, by the target.
 *
 * @param variants variant index to what it parsed, in variant order
 * @param runnable false when it was parsed by an argument rebuilt on a client, which cannot run
 */
public record ParsedOverload(Map<Integer, Object> variants, boolean runnable) {

    public ParsedOverload {
        variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
    }

    /** A placeholder, which every variant accepts alike. */
    static ParsedOverload forAll(int variantCount, Object form, boolean runnable) {
        Map<Integer, Object> variants = new LinkedHashMap<>();
        for (int i = 0; i < variantCount; i++) {
            variants.put(i, form);
        }
        return new ParsedOverload(variants, runnable);
    }

    public List<Integer> indices() {
        return List.copyOf(variants.keySet());
    }

    public Object value(int variant) {
        return variants.get(variant);
    }
}
