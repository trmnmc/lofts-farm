package place.games.loftsfarm.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything one player's farm knows. One instance per player. */
public final class FarmState {
    private final Wallet wallet;
    private final Storage storage = new Storage();
    private final Map<String, PlantRecord> plants = new HashMap<>();
    private int plotsOwned = PlotPricing.START_PLOTS;

    public FarmState(long startingCash) {
        this.wallet = new Wallet(startingCash);
    }

    public Wallet wallet() { return wallet; }
    public Storage storage() { return storage; }
    public Map<String, PlantRecord> plants() { return plants; }
    public int plotsOwned() { return plotsOwned; }

    public void put(PlantRecord r) { plants.put(r.key(), r); }
    public PlantRecord remove(String key) { return plants.remove(key); }

    /** @return true if a plot was added; false if already at PlotPricing.MAX_PLOTS. */
    public boolean incrementPlots() {
        if (plotsOwned >= PlotPricing.MAX_PLOTS) return false;
        plotsOwned++;
        return true;
    }

    /**
     * Sets plotsOwned directly. Used by StateCodec when loading a saved
     * value -- not clamped against MAX_PLOTS, the same trust level given to
     * every other field a save file can populate.
     */
    public void setPlotsOwned(int n) {
        if (n < 0) throw new IllegalArgumentException("plotsOwned cannot be negative");
        this.plotsOwned = n;
    }

    /**
     * Sells every unit of one crop in storage at its raw value, crediting the
     * wallet. Returns empty and changes nothing if storage holds none of it.
     */
    public Optional<SaleLine> sell(Crop crop) {
        int n = storage.count(crop);
        if (n <= 0 || !storage.take(crop, n)) return Optional.empty();
        long gross = (long) n * crop.rawValue();
        wallet.credit(gross);
        return Optional.of(new SaleLine(crop, n, gross));
    }

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
