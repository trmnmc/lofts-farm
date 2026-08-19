package place.games.loftsfarm.domain;

/**
 * Wall-clock crop growth. Ripeness is always derived by comparing readyAt
 * against a supplied now-in-millis — never by counting ticks. That is what
 * makes growth correct while the chunk is unloaded and the owner offline.
 */
public record GrowthClock(long plantedAt, long readyAt) {

    public static GrowthClock start(Crop crop, long nowMs) {
        return new GrowthClock(nowMs, nowMs + crop.growSeconds() * 1000L);
    }

    public boolean ripe(long nowMs) {
        return nowMs >= readyAt;
    }

    public long remainingMs(long nowMs) {
        return Math.max(0L, readyAt - nowMs);
    }

    /** 0-based visual stage, clamped to the crop's final stage. */
    public int stage(Crop crop, long nowMs) {
        int last = crop.stageCount() - 1;
        long total = readyAt - plantedAt;
        if (total <= 0L) return last;
        long elapsed = Math.max(0L, nowMs - plantedAt);
        long s = elapsed * last / total;
        return (int) Math.min(last, s);
    }

    /**
     * Wall-clock ms at which the given 0-based stage first becomes the value
     * stage() returns -- i.e. the smallest nowMs for which
     * stage(crop, nowMs) >= stage. Derived directly from plantedAt/readyAt/
     * stageCount every time, never by accumulating per-stage deltas, so it
     * cannot drift out of sync with stage() no matter how many times it is
     * called.
     *
     * stage <= 0 returns plantedAt; stage >= last returns readyAt exactly.
     * That exactness matters: a chain of tasks scheduled one boundary at a
     * time must have its final link land precisely on readyAt.
     */
    public long stageBoundaryMs(Crop crop, int stage) {
        int last = crop.stageCount() - 1;
        if (stage <= 0) return plantedAt;
        if (stage >= last) return readyAt;
        long total = readyAt - plantedAt;
        if (total <= 0L) return plantedAt;
        // stage(nowMs) is floor(elapsed*last/total). The smallest elapsed for
        // which that is >= stage is ceil(stage*total/last).
        long numerator = (long) stage * total;
        long elapsed = (numerator + last - 1) / last;
        return plantedAt + elapsed;
    }

    /** Fertiliser: halve what remains. Never extends, never rewinds plantedAt. */
    public GrowthClock halved(long nowMs) {
        long remaining = remainingMs(nowMs);
        if (remaining <= 0L) return this;
        return new GrowthClock(plantedAt, nowMs + remaining / 2L);
    }
}
