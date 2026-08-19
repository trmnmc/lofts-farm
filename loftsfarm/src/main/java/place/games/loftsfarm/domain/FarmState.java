package place.games.loftsfarm.domain;

import java.util.HashMap;
import java.util.Map;

/** Everything one player's farm knows. One instance per player. */
public final class FarmState {
    private final Wallet wallet;
    private final Storage storage = new Storage();
    private final Map<String, PlantRecord> plants = new HashMap<>();

    public FarmState(long startingCash) {
        this.wallet = new Wallet(startingCash);
    }

    public Wallet wallet() { return wallet; }
    public Storage storage() { return storage; }
    public Map<String, PlantRecord> plants() { return plants; }

    public void put(PlantRecord r) { plants.put(r.key(), r); }
    public PlantRecord remove(String key) { return plants.remove(key); }
}
