package place.games.loftsfarm.test;

public final class TestMain {
    public static void main(String[] args) {
        System.out.println("LoftsFarm domain tests");
        GrowthClockTest.run();
        System.exit(Check.report());
    }
}
