package g_mungus.munguscript.conformance;

import g_mungus.munguscript.conformance.TestTypes.Color;
import g_mungus.munguscript.conformance.TestTypes.Counter;

import java.util.ArrayList;
import java.util.List;

/** What the test getters read and the test executors write. Each harness has its own. */
final class World {

    /** One executor run, with the value it acted on. */
    record Call(String executor, Object value) {
    }

    final List<Call> calls = new ArrayList<>();
    final Counter counter = new Counter(0);
    Color favourite = Color.RED;
    int level = 0;
    String message = "";

    int record(String executor, Object value) {
        calls.add(new Call(executor, value));
        return 1;
    }

    static Call call(String executor, Object value) {
        return new Call(executor, value);
    }
}
