package place.games.loftsfarm.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Returns a per-crop record of what sold, in Crop enum order, so callers
     * can report it deterministically. Storage is emptied only for crops
     * that actually sold.
     */
    public List<SaleLine> sellAll() {
        List<SaleLine> sold = new ArrayList<>();
        for (Crop c : Crop.values()) {
            int n = storage.count(c);
            if (n > 0 && storage.take(c, n)) {
                long gross = (long) n * c.rawValue();
                wallet.credit(gross);
                sold.add(new SaleLine(c, n, gross));
            }
        }
        return sold;
    }
}
