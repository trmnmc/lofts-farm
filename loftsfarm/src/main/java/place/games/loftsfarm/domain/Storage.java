package place.games.loftsfarm.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * Virtual farm storage. Harvests go here, not into the player's inventory —
 * matching v1's COLLECT harvest mode and keeping inventory management out of
 * the fast loop.
 */
public final class Storage {
    private final Map<Crop, Integer> held = new EnumMap<>(Crop.class);

    public int count(Crop crop) {
        return held.getOrDefault(crop, 0);
    }

    public void add(Crop crop, int n) {
        if (n < 0) throw new IllegalArgumentException("add cannot be negative");
        held.merge(crop, n, Integer::sum);
    }

    /** @return true if taken in full; false leaves storage untouched. */
    public boolean take(Crop crop, int n) {
        if (n < 0) throw new IllegalArgumentException("take cannot be negative");
        int have = count(crop);
        if (have < n) return false;
        held.put(crop, have - n);
        return true;
    }

    public Map<Crop, Integer> snapshot() {
        return new EnumMap<>(held);
    }
}
