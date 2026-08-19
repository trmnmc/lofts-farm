package place.games.loftsfarm.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import place.games.loftsfarm.domain.PlantRecord;

import java.util.HashMap;
import java.util.Map;

/**
 * One scheduled task per plant, firing at its readyAt. Never a sweep over all
 * plots — that is a hard constraint from the spec, because a global poll is the
 * whole performance budget at 50+ farms.
 *
 * Intermediate visual stages are painted only when a player is close enough to
 * see them. When nobody is nearby the block is corrected on the next paint.
 */
public final class GrowthScheduler {
    private static final double PAINT_RADIUS_SQ = 64.0 * 64.0;

    private final Plugin plugin;
    private final Map<String, BukkitTask> tasks = new HashMap<>();

    public GrowthScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void schedule(PlantRecord record) {
        cancel(record.key());
        long delayMs = record.clock().remainingMs(System.currentTimeMillis());
        // Ceiling, not floor: a task scheduled mid-tick that floors can fire up
        // to 49ms before readyAt, so ripe() reads false and it paints the
        // second-to-last stage instead of the ripe one, self-removing and
        // leaving the crop looking unripe until the next restart re-schedules it.
        long ticks = Math.max(1L, (delayMs + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(record.key());
            paintStage(record);
        }, ticks);
        tasks.put(record.key(), task);
    }

    public void cancel(String key) {
        BukkitTask t = tasks.remove(key);
        if (t != null) t.cancel();
    }

    public void cancelAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    /**
     * Sets the block's age to match elapsed real time.
     *
     * Intermediate stages are skipped when nobody is close enough to see them —
     * the spec only requires interpolation for chunks with a player in view
     * distance. A ripe crop is always painted regardless of who is watching,
     * because its final state must be correct whenever it is next observed.
     */
    public void paintStage(PlantRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return;
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) return;

        boolean ripe = record.clock().ripe(System.currentTimeMillis());
        if (!ripe && !anyPlayerNear(record)) return;

        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return;

        int stage = record.clock().stage(record.crop(), System.currentTimeMillis());
        int target = Math.min(ageable.getMaximumAge(), stage);
        if (ageable.getAge() != target) {
            ageable.setAge(target);
            block.setBlockData(ageable, false);
        }
    }

    private boolean anyPlayerNear(PlantRecord record) {
        World world = Bukkit.getWorld(record.world());
        if (world == null) return false;
        Location at = new Location(world, record.x(), record.y(), record.z());
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) < PAINT_RADIUS_SQ) return true;
        }
        return false;
    }
}
