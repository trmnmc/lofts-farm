package place.games.loftsfarm.domain;

/**
 * Crop definitions. Every number here comes from the browser reference build,
 * NOT from the live v1 config.yml.
 *
 * stageCount is the number of distinct visual stages the block can show,
 * including stage 0 (just sown) and the final ripe stage.
 */
public enum Crop {
    WHEAT("wheat", "Wheat", 3L, 30, 20L, 8, 8),
    CARROT("carrot", "Carrot", 5L, 45, 30L, 7, 8);

    private final String id;
    private final String displayName;
    private final long seedCost;
    private final int growSeconds;
    private final long rawValue;
    private final int plantings;
    private final int stageCount;

    Crop(String id, String displayName, long seedCost, int growSeconds,
         long rawValue, int plantings, int stageCount) {
        this.id = id;
        this.displayName = displayName;
        this.seedCost = seedCost;
        this.growSeconds = growSeconds;
        this.rawValue = rawValue;
        this.plantings = plantings;
        this.stageCount = stageCount;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public long seedCost() { return seedCost; }
    public int growSeconds() { return growSeconds; }
    public long rawValue() { return rawValue; }
    public int plantings() { return plantings; }
    public int stageCount() { return stageCount; }

    public static Crop byId(String id) {
        if (id == null) return null;
        for (Crop c : values()) if (c.id.equals(id)) return c;
        return null;
    }
}
