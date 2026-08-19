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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

import java.util.EnumSet;
import java.util.Set;

public final class FarmListener implements Listener {

    /**
     * The only block types onPhysics could ever care about. BlockPhysicsEvent
     * fires on nearly every block change in the world -- water flow, redstone,
     * falling sand, pistons -- so this check must happen before any farm-state
     * lookup, not after it.
     */
    private static final Set<Material> CROP_MATERIALS =
            EnumSet.of(Material.WHEAT, Material.CARROTS);

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

    /**
     * Catches crops lost to something other than a player breaking the block:
     * water flow, pistons, trampling, explosions, WorldEdit, or farmland simply
     * drying out from under the crop. None of those go through
     * BlockBreakEvent, so without this, the record/owner entry/scheduled task
     * for a destroyed crop would live on forever pointing at air -- re-saved
     * every shutdown and rescheduled every boot, with farms.txt growing
     * without bound.
     *
     * BlockPhysicsEvent is the mechanism vanilla uses to re-check "can this
     * block still exist here" whenever a neighbour changes -- it is exactly
     * how a crop's supporting farmland being pulled out from under it gets
     * detected in the first place (a crop is a BushBlock; its updateShape
     * hook, which fires through this event, is what turns it back into a
     * dropped item when canSurvive() fails). It is not tied to *why* the
     * neighbour changed, so it catches all of the causes above uniformly,
     * unlike listening for one specific cause (e.g. only BlockFadeEvent, which
     * covers farmland drying out naturally but not trampling, pistons,
     * explosions or WorldEdit).
     *
     * Two directions are checked because we cannot assume which block's
     * physics actually gets re-evaluated first:
     *  - the tracked crop's own physics fires (its support may have just
     *    failed) -- checked by looking at the block directly below it;
     *  - the block below a tracked crop has its own physics fire because IT
     *    changed -- checked from that block's perspective, in case the crop
     *    above has not (yet) had its own physics re-run in the same tick.
     * discardAt() is idempotent (returns false harmlessly if already
     * untracked), so the overlap between the two checks is not a problem.
     *
     * BOTH directions are gated on a cheap Material check before either one
     * touches farm state. farms.at()/discardAt() build a String key
     * (world name + three coordinates) and hit a HashMap -- trivial for one
     * call, but BlockPhysicsEvent can fire thousands of times per second on a
     * server with active redstone or water, so that cost must never be paid
     * for a block we could not possibly care about. Checking
     * "is this block a crop" or "is the block above a crop" first (plain
     * Material reads, no hashing) throws out nearly every firing before any
     * hashmap lookup happens; the earlier "not farmland" guard alone did not
     * do this, since almost every block in the game is not farmland.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Block above = block.getRelative(0, 1, 0);
        Material aboveType = above.getType();

        boolean selfIsCrop = CROP_MATERIALS.contains(type);
        boolean aboveIsCrop = CROP_MATERIALS.contains(aboveType);
        if (!selfIsCrop && !aboveIsCrop) return;

        if (selfIsCrop) {
            farms.at(block).ifPresent(rec -> {
                if (block.getRelative(0, -1, 0).getType() != Material.FARMLAND) {
                    farms.discardAt(block);
                }
            });
        }

        if (aboveIsCrop && type != Material.FARMLAND) {
            farms.at(above).ifPresent(rec -> farms.discardAt(above));
        }
    }

    /**
     * MONITOR, not the default NORMAL: a protection plugin cancelling at
     * HIGHEST runs *after* NORMAL, so a NORMAL-priority handler would already
     * have mutated state (deleted the record, paid out, dropped items) before
     * the protection plugin's cancellation ever happens -- rechecking
     * isCancelled() at the top of a NORMAL handler cannot fix that, since the
     * cancellation has not occurred yet at that point in the same event
     * dispatch. Only running after every other priority tier, including
     * HIGHEST, observes the final cancellation state before mutating.
     * MONITOR's "read only" convention is a soft guideline, not a framework
     * restriction; combined with ignoreCancelled = true it means this handler
     * simply never runs at all once anything upstream has cancelled the
     * event, which is exactly the property needed here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
     * Breaking a tracked crop must always untrack it, ripe or not -- otherwise
     * the block is gone while the record and its scheduled task live on,
     * pointing at air. Ripe crops still pay out; unripe ones are simply
     * discarded.
     *
     * MONITOR, same reasoning as onInteract above: this must run after any
     * protection plugin's HIGHEST-priority cancellation, or the record gets
     * deleted here while the block itself survives because the break was
     * blocked -- leaving a live crop block with no tracking, so vanilla growth
     * resumes on it and the "vanilla growth never advances a tracked plant"
     * guarantee is broken.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (farms.at(block).isEmpty()) return;

        event.setDropItems(false);
        if (!farms.harvestAt(event.getPlayer(), block)) {
            // Not ripe (or not the owner's) -- drop the record so nothing is orphaned.
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
