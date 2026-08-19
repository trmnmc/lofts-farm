package place.games.loftsfarm.bukkit;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

public final class FarmListener implements Listener {

    private final Plugin plugin;
    private final FarmService farms;

    public FarmListener(Plugin plugin, FarmService farms) {
        this.plugin = plugin;
        this.farms = farms;
    }

    /** Vanilla growth must never advance a tracked plant. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (farms.at(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack held = player.getInventory().getItemInMainHand();

            // Fertilise a growing crop.
            if (Items.isFertiliser(plugin, held) && farms.at(block).isPresent()) {
                event.setCancelled(true);
                if (farms.fertiliseAt(player, block)) {
                    if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
                }
                return;
            }

            // Harvest a ripe crop.
            if (farms.at(block).isPresent()) {
                event.setCancelled(true);
                if (farms.harvestAt(player, block)) {
                    block.setType(Material.AIR);
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Harvested to farm storage.");
                } else {
                    farms.at(block).ifPresent(r -> player.sendMessage(
                            "\u00A78[\u00A7c!\u00A78] \u00A77Not ready \u2014 \u00A7f"
                            + (r.clock().remainingMs(System.currentTimeMillis()) / 1000) + "s\u00A77 left."));
                }
                return;
            }

            // Sow from the hand.
            SeedStack stack = Items.readSeed(plugin, held);
            if (stack != null && block.getType() == Material.FARMLAND) {
                event.setCancelled(true);
                Block above = block.getRelative(0, 1, 0);
                if (!above.getType().isAir()) {
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Something is already there.");
                    return;
                }
                above.setType(cropMaterial(stack.crop()));
                if (above.getBlockData() instanceof Ageable a) {
                    a.setAge(0);
                    above.setBlockData(a, false);
                }
                farms.plantAt(player, above, stack);

                SeedStack after = stack.usedOnce();
                if (after.spent()) {
                    held.setAmount(held.getAmount() - 1);
                    player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77That seed wore out.");
                } else {
                    held.setItemMeta(Items.seed(plugin, after).getItemMeta());
                }
            }
        }
    }

    /**
     * Breaking a tracked crop must always untrack it, ripe or not — otherwise the
     * block is gone while the record and its scheduled task live on, pointing at
     * air. Ripe crops still pay out; unripe ones are simply discarded.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (farms.at(block).isEmpty()) return;

        event.setDropItems(false);
        if (!farms.harvestAt(event.getPlayer(), block)) {
            // Not ripe (or not the owner's) — drop the record so nothing is orphaned.
            farms.discardAt(block);
            event.getPlayer().sendMessage(
                    "§8[§c!§8] §7That crop wasn't ready. Lost it.");
        }
    }

    private static Material cropMaterial(Crop crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
        };
    }
}
