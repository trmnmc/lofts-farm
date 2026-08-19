package place.games.loftsfarm.domain;

/**
 * A seed item's remaining plantings. Immutable — planting produces a new stack.
 *
 * Durability rather than a recovery chance, deliberately: it yields the same
 * expected sink as a probability roll with no bad-luck spirals on an expensive
 * seed, and it is legible on the item.
 */
public record SeedStack(Crop crop, int remaining) {

    public SeedStack {
        if (crop == null) throw new IllegalArgumentException("crop required");
        if (remaining < 0) throw new IllegalArgumentException("remaining cannot be negative");
        if (remaining > crop.plantings()) {
            throw new IllegalArgumentException("remaining exceeds this crop's plantings");
        }
    }

    public static SeedStack fresh(Crop crop) {
        return new SeedStack(crop, crop.plantings());
    }

    public boolean spent() { return remaining == 0; }

    public SeedStack usedOnce() {
        if (spent()) throw new IllegalStateException("seed is spent");
        return new SeedStack(crop, remaining - 1);
    }

    /**
     * Maps remaining plantings onto a vanilla damage value so the player sees a
     * familiar wearing-down bar. A full seed reports 0 damage.
     */
    public int damageFor(int maxDurability) {
        int used = crop.plantings() - remaining;
        return (int) ((long) used * maxDurability / crop.plantings());
    }
}
