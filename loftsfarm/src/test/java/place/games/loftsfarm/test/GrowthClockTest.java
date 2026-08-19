package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.GrowthClock;

public final class GrowthClockTest {
    public static void run() {
        long t0 = 1_000_000L;

        Check.eq("wheat grow seconds", Crop.WHEAT.growSeconds(), 30);
        Check.eq("wheat seed cost", Crop.WHEAT.seedCost(), 3L);
        Check.eq("wheat raw value", Crop.WHEAT.rawValue(), 20L);
        Check.eq("wheat plantings", Crop.WHEAT.plantings(), 8);
        Check.eq("carrot grow seconds", Crop.CARROT.growSeconds(), 45);
        Check.eq("carrot plantings", Crop.CARROT.plantings(), 7);
        Check.eq("lookup by id", Crop.byId("carrot"), Crop.CARROT);
        Check.eq("unknown id is null", Crop.byId("nope"), null);

        GrowthClock g = GrowthClock.start(Crop.WHEAT, t0);
        Check.eq("ready 30s later", g.readyAt(), t0 + 30_000L);

        Check.isFalse("not ripe at plant time", g.ripe(t0));
        Check.isFalse("not ripe one ms early", g.ripe(t0 + 29_999L));
        Check.isTrue("ripe exactly on time", g.ripe(t0 + 30_000L));
        Check.isTrue("ripe later", g.ripe(t0 + 60_000L));

        Check.eq("stage 0 at plant", g.stage(Crop.WHEAT, t0), 0);
        Check.eq("stage 3 at half way", g.stage(Crop.WHEAT, t0 + 15_000L), 3);
        Check.eq("stage maxes at ripe", g.stage(Crop.WHEAT, t0 + 30_000L), Crop.WHEAT.stageCount() - 1);
        Check.eq("stage clamped past ripe", g.stage(Crop.WHEAT, t0 + 90_000L), Crop.WHEAT.stageCount() - 1);

        Check.eq("remaining at plant", g.remainingMs(t0), 30_000L);
        Check.eq("remaining never negative", g.remainingMs(t0 + 99_000L), 0L);

        // Fertiliser halves what is LEFT, not the total.
        GrowthClock h = g.halved(t0 + 10_000L);
        Check.eq("halved leaves 10s of the remaining 20s", h.remainingMs(t0 + 10_000L), 10_000L);
        Check.eq("halving does not move plantedAt", h.plantedAt(), g.plantedAt());

        GrowthClock ripe = GrowthClock.start(Crop.WHEAT, t0).halved(t0 + 30_000L);
        Check.eq("halving a ripe crop is a no-op", ripe.remainingMs(t0 + 30_000L), 0L);
    }
}
