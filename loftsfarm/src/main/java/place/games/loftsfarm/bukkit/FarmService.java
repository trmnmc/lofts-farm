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

        state(player.getUniqueId()).put(record);
        owners.put(record.key(), player.getUniqueId());
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
        state(owner).remove(key);
        owners.remove(key);
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
            UUID current = null;
            StringBuilder chunk = new StringBuilder();
            for (String line : all.split("\n")) {
                if (line.startsWith("player ")) {
                    current = UUID.fromString(line.substring(7).trim());
                    chunk.setLength(0);
                } else if (line.trim().equals("end") && current != null) {
                    FarmState st = StateCodec.read(chunk.toString());
                    states.put(current, st);
                    for (PlantRecord r : st.plants().values()) {
                        owners.put(r.key(), current);
                        scheduler.schedule(r);
                        scheduler.paintStage(r);
                    }
                    current = null;
                } else {
                    chunk.append(line).append('\n');
                }
            }
            plugin.getLogger().info("Loaded " + states.size() + " farm(s).");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load farms: " + e.getMessage());
        }
    }
}
