package place.games.loftsfarm.test;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free assertions. No JUnit is available in this environment. */
public final class Check {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed = 0;

    private Check() {}

    public static void eq(String label, Object actual, Object expected) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            FAILURES.add(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void isTrue(String label, boolean cond) {
        if (cond) passed++; else FAILURES.add(label + ": expected true");
    }

    public static void isFalse(String label, boolean cond) {
        if (!cond) passed++; else FAILURES.add(label + ": expected false");
    }

    public static void throwsUp(String label, Runnable r) {
        try {
            r.run();
            FAILURES.add(label + ": expected an exception, none thrown");
        } catch (RuntimeException expected) {
            passed++;
        }
    }

    public static int report() {
        System.out.println();
        System.out.println(passed + " passed, " + FAILURES.size() + " failed");
        for (String f : FAILURES) System.out.println("  FAIL  " + f);
        return FAILURES.isEmpty() ? 0 : 1;
    }
}
