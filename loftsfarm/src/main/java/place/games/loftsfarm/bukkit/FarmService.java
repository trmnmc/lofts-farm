package place.games.loftsfarm.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Owns per-player farm state, persistence, and the plant/harvest/fertilise verbs. */
public final class FarmService {
    private static final long STARTING_CASH = 250L;

    private final Plugin plugin;
    private final GrowthScheduler scheduler;
    private final Map<UUID, FarmState> states = new HashMap<>();
    private final Map<String, UUID> owners = new HashMap<>();

    /**
     * Plant keys grouped by chunk, so a ChunkLoadEvent can find "any tracked
     * plants in this chunk" with one map lookup instead of scanning every
     * tracked plant on the server. Keyed by "world:chunkX:chunkZ", derived
     * from a record's own stored coordinates (never by touching the world),
     * and kept in sync at every insert/remove of a PlantRecord.
     */
    private final Map<String, Set<String>> plantsByChunk = new HashMap<>();

    public FarmService(Plugin plugin, GrowthScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public FarmState state(UUID id) {
        return states.computeIfAbsent(id, k -> new FarmState(STARTING_CASH));
    }

    public UUID ownerOf(Block block) {
        return owners.get(keyOf(block));
    }

    private static String keyOf(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static String chunkKeyOf(String world, int blockX, int blockZ) {
        return world + ":" + (blockX >> 4) + ":" + (blockZ >> 4);
    }

    private void indexChunk(PlantRecord r) {
        plantsByChunk.computeIfAbsent(chunkKeyOf(r.world(), r.x(), r.z()), k -> new HashSet<>())
                .add(r.key());
    }

    private void unindexChunk(PlantRecord r) {
        String chunkKey = chunkKeyOf(r.world(), r.x(), r.z());
        Set<String> keys = plantsByChunk.get(chunkKey);
        if (keys == null) return;
        keys.remove(r.key());
        if (keys.isEmpty()) plantsByChunk.remove(chunkKey);
    }

    /**
     * Tracked plants in one chunk. O(1) average (a single map lookup plus one
     * lookup per plant actually in that chunk) — never a scan over every
     * tracked plant on the server. That is what makes it safe to call from a
     * ChunkLoadEvent handler, which can fire many times per second.
     */
    public List<PlantRecord> plantsInChunk(String world, int chunkX, int chunkZ) {
        Set<String> keys = plantsByChunk.get(world + ":" + chunkX + ":" + chunkZ);
        if (keys == null || keys.isEmpty()) return List.of();
        List<PlantRecord> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            UUID owner = owners.get(key);
            if (owner == null) continue;
            PlantRecord r = state(owner).plants().get(key);
            if (r != null) result.add(r);
        }
        return result;
    }

    /**
     * Called when a chunk finishes loading. Repaints every tracked plant in
     * that chunk to the stage its timestamp implies, and reschedules its
     * growth chain.
     *
     * This is the fix for the case paintStage alone cannot cover: while a
     * chunk is unloaded, paintStage silently bails on every attempt, and if
     * the plant reaches readyAt during that time the chain has nothing left
     * to schedule (it is legitimately done) — so nothing would ever repaint
     * the block again without this hook, even though the record is correct
     * and harvesting would succeed.
     */
    public void onChunkLoad(String world, int chunkX, int chunkZ) {
        for (PlantRecord r : plantsInChunk(world, chunkX, chunkZ)) {
            scheduler.schedule(r);
            scheduler.paintStage(r);
        }
    }

    public Optional<PlantRecord> at(Block block) {
        UUID owner = ownerOf(block);
        if (owner == null) return Optional.empty();
        return Optional.ofNullable(state(owner).plants().get(keyOf(block)));
    }

    public PlantRecord plantAt(Player player, Block block, SeedStack stack) {
        long now = System.currentTimeMillis();
        GrowthClock clock = GrowthClock.start(stack.crop(), now);
        PlantRecord record = new PlantRecord(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                stack.crop(), clock.plantedAt(), clock.readyAt());

        // A stale record can already occupy this key: an orphan left by a
        // destruction path this listener does not detect, or a record that
        // survived a restart. Clear it from its previous owner's state and
        // cancel its task first, or ownership would straddle two players
        // against the same key -- the old owner keeps a record pointing at a
        // block that is now someone else's crop, and could win the key back
        // on the next load().
        UUID previousOwner = owners.get(record.key());
        if (previousOwner != null) {
            state(previousOwner).remove(record.key());
            scheduler.cancel(record.key());
        }

        state(player.getUniqueId()).put(record);
        owners.put(record.key(), player.getUniqueId());
        indexChunk(record);
        scheduler.schedule(record);
        scheduler.paintStage(record);
        return record;
    }

    public boolean harvestAt(Player player, Block block) {
        UUID owner = ownerOf(block);
        if (owner == null || !owner.equals(player.getUniqueId())) return false;
        FarmState st = state(owner);
        PlantRecord rec = st.plants().get(keyOf(block));
        if (rec == null || !rec.clock().ripe(System.currentTimeMillis())) return false;

        st.remove(rec.key());
        owners.remove(rec.key());
        unindexChunk(rec);
        scheduler.cancel(rec.key());
        st.storage().add(rec.crop(), 1);
        return true;
    }

    /**
     * Untracks a plant without paying out. Used when a crop is destroyed before
     * it ripens, so no record or scheduled task is left pointing at air.
     */
    public boolean discardAt(Block block) {
        UUID owner = ownerOf(block);
        if (owner == null) return false;
        String key = keyOf(block);
        PlantRecord rec = state(owner).plants().get(key);
        state(owner).remove(key);
        owners.remove(key);
        if (rec != null) unindexChunk(rec);
        scheduler.cancel(key);
        return true;
    }

    public boolean fertiliseAt(Player player, Block block) {
        UUID owner = ownerOf(block);
        if (owner == null || !owner.equals(player.getUniqueId())) return false;
        FarmState st = state(owner);
        PlantRecord rec = st.plants().get(keyOf(block));
        if (rec == null) return false;

        long now = System.currentTimeMillis();
        long remaining = rec.clock().remainingMs(now);
        if (remaining <= 0L) return false;

        long cost = Fertiliser.costFor(rec.crop(), remaining);
        if (!st.wallet().spend(cost)) return false;

        PlantRecord updated = rec.withClock(rec.clock().halved(now));
        st.put(updated);
        scheduler.schedule(updated);
        scheduler.paintStage(updated);
        player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Fertilised for \u00A7f$" + cost
                + "\u00A77. Halved the wait.");
        return true;
    }

    // ---- persistence ----

    private Path file() {
        return plugin.getDataFolder().toPath().resolve("farms.txt");
    }

    public void save() {
        StringBuilder sb = new StringBuilder();
        states.forEach((id, st) -> {
            sb.append("player ").append(id).append('\n');
            sb.append(StateCodec.write(st));
            sb.append("end\n");
        });
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), sb.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save farms: " + e.getMessage());
        }
    }

    public void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            String all = Files.readString(f);
            String currentIdText = null;
            boolean inBlock = false;
            StringBuilder chunk = new StringBuilder();
            int failures = 0;
            List<String> failedIds = new ArrayList<>();

            for (String line : all.split("\n")) {
                if (line.startsWith("player ")) {
                    currentIdText = line.substring(7).trim();
                    chunk.setLength(0);
                    inBlock = true;
                } else if (line.trim().equals("end") && inBlock) {
                    // Each player's block is parsed in isolation. A malformed
                    // UUID or a body StateCodec cannot make sense of must not
                    // abort the whole load -- otherwise every player after the
                    // bad line silently gets a fresh 250-cash state, and the
                    // next shutdown's save() overwrites farms.txt with that
                    // partial data. One bad line would otherwise destroy every
                    // farm after it.
                    try {
                        UUID id = UUID.fromString(currentIdText);
                        FarmState st = StateCodec.read(chunk.toString());
                        states.put(id, st);
                        for (PlantRecord r : st.plants().values()) {
                            owners.put(r.key(), id);
                            indexChunk(r);
                            scheduler.schedule(r);
                            scheduler.paintStage(r);
                        }
                        for (String skipped : StateCodec.lastSkipped()) {
                            plugin.getLogger().warning("Skipped malformed line for player "
                                    + currentIdText + ": " + skipped);
                        }
                    } catch (Exception e) {
                        failures++;
                        failedIds.add(String.valueOf(currentIdText));
                        plugin.getLogger().warning("Could not load farm for player "
                                + currentIdText + ": " + e.getMessage());
                    }
                    inBlock = false;
                    currentIdText = null;
                } else if (inBlock) {
                    chunk.append(line).append('\n');
                }
            }

            String summary = "Loaded " + states.size() + " farm(s).";
            if (failures > 0) {
                summary += " " + failures + " farm(s) could not be loaded and were skipped: "
                        + String.join(", ", failedIds);
            }
            plugin.getLogger().info(summary);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load farms: " + e.getMessage());
        }
    }
}
