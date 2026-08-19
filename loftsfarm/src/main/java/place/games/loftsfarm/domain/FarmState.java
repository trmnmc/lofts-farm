package place.games.loftsfarm.domain;

import java.util.EnumMap;
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

    /**
     * Sells every crop in storage at its raw value, crediting the wallet.
     * Returns a per-crop record of what sold, so callers can report it.
     * Storage is emptied only for crops that actually sold.
     */
    public Map<Crop, Long> sellAll() {
        Map<Crop, Long> sold = new EnumMap<>(Crop.class);
        for (Crop c : Crop.values()) {
            int n = storage.count(c);
            if (n > 0 && storage.take(c, n)) {
                long gross = (long) n * c.rawValue();
                wallet.credit(gross);
                sold.put(c, gross);
            }
        }
        return sold;
    }
}
