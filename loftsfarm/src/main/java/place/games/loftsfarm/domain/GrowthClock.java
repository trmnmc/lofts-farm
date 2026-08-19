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

    /** Fertiliser: halve what remains. Never extends, never rewinds plantedAt. */
    public GrowthClock halved(long nowMs) {
        long remaining = remainingMs(nowMs);
        if (remaining <= 0L) return this;
        return new GrowthClock(plantedAt, nowMs + remaining / 2L);
    }
}
