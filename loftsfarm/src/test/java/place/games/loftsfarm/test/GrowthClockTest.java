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

        // Stage-boundary computation: the timestamp at which each stage
        // becomes the correct stage() result. This is what the growth-task
        // chain schedules against, so it must line up exactly with stage(),
        // never drift, and its last link must land on readyAt.
        int last = Crop.WHEAT.stageCount() - 1;
        Check.eq("stage 0 boundary is plantedAt", g.stageBoundaryMs(Crop.WHEAT, 0), t0);
        Check.eq("negative stage clamps to plantedAt", g.stageBoundaryMs(Crop.WHEAT, -1), t0);

        long midBoundary = g.stageBoundaryMs(Crop.WHEAT, 4);
        Check.eq("stage() at its own boundary reads that stage",
                g.stage(Crop.WHEAT, midBoundary), 4);
        Check.eq("one ms before a boundary still reads the previous stage",
                g.stage(Crop.WHEAT, midBoundary - 1), 3);

        Check.eq("final stage boundary lands exactly on readyAt",
                g.stageBoundaryMs(Crop.WHEAT, last), g.readyAt());
        Check.eq("a stage past the last one also lands on readyAt",
                g.stageBoundaryMs(Crop.WHEAT, last + 3), g.readyAt());

        // Every boundary in sequence must be non-decreasing and each must
        // agree with stage() at the instant it fires -- this is the exact
        // property the chained scheduler depends on to never skip or repeat
        // a stage.
        long previous = g.plantedAt();
        for (int s = 1; s <= last; s++) {
            long boundary = g.stageBoundaryMs(Crop.WHEAT, s);
            Check.isTrue("boundary for stage " + s + " is not before the previous boundary",
                    boundary >= previous);
            Check.eq("stage() at boundary for stage " + s + " reads " + s,
                    g.stage(Crop.WHEAT, boundary), s);
            previous = boundary;
        }
    }
}
