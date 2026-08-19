package place.games.loftsfarm.domain;

/** One tracked plant at one block position. */
public record PlantRecord(String world, int x, int y, int z,
                          Crop crop, long plantedAt, long readyAt) {

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public GrowthClock clock() {
        return new GrowthClock(plantedAt, readyAt);
    }

    public PlantRecord withClock(GrowthClock c) {
        return new PlantRecord(world, x, y, z, crop, c.plantedAt(), c.readyAt());
    }
}
