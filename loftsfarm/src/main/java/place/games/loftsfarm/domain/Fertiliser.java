package place.games.loftsfarm.domain;

/**
 * Fertiliser is the toll between the slow half of the game and the fast half.
 *
 * Priced off what the crop is worth — never off what the seed cost — and scaled
 * by how much time is left, so applying it is always net-positive (P5).
 */
public final class Fertiliser {
    private static final double VALUE_FACTOR = 0.26d;
    private static final long MINIMUM = 4L;

    private Fertiliser() {}

    public static long costFor(Crop crop, long remainingMs) {
        long total = crop.growSeconds() * 1000L;
        double share = total <= 0L ? 0d : Math.min(1d, (double) Math.max(0L, remainingMs) / total);
        long scaled = Math.round(crop.rawValue() * VALUE_FACTOR * share);
        return Math.max(MINIMUM, scaled);
    }
}
