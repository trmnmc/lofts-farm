package place.games.loftsfarm.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.PlotPricing;
import place.games.loftsfarm.domain.SeedStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Smart Hoe's four views: hub, seed shop, storage, and plot-purchase
 * confirmation. One hotbar item is the whole interface -- no slash commands
 * needed mid-scramble.
 *
 * Same rules as BoardGui, which this reuses for Orders rather than
 * reimplementing: redrawn on state change only, never on a schedule;
 * identified by a dedicated InventoryHolder per view rather than by title;
 * both click and drag are cancelled so nothing can be dragged in or out.
 */
public final class HoeGui implements Listener {
    private static final String PREFIX = "§8[§c!§8] §7";

    private static final int HUB_SLOT_ORDERS = 10;
    private static final int HUB_SLOT_SEEDS = 12;
    private static final int HUB_SLOT_STORAGE = 14;
    private static final int HUB_SLOT_INFO = 16;

    private static final int BACK_SLOT = 22;

    private static final int PLOT_INFO_SLOT = 13;
    private static final int PLOT_CONFIRM_SLOT = 11;
    private static final int PLOT_CANCEL_SLOT = 15;

    private final Plugin plugin;
    private final FarmService farms;
    private final BoardGui board;

    public HoeGui(Plugin plugin, FarmService farms, BoardGui board) {
        this.plugin = plugin;
        this.farms = farms;
        this.board = board;
    }

    // ---- Hub ----

    public void openHub(Player player) {
        HubHolder holder = new HubHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Smart Hoe");
        holder.inventory = inv;
        renderHub(player, inv);
        player.openInventory(inv);
    }

    private void renderHub(Player player, Inventory inv) {
        inv.clear();
        FarmState st = farms.state(player.getUniqueId());

        inv.setItem(HUB_SLOT_ORDERS, icon(Material.WRITTEN_BOOK, "§eOrders",
                List.of("§7Open the order board")));
        inv.setItem(HUB_SLOT_SEEDS, icon(Material.WHEAT_SEEDS, "§eSeed Shop",
                List.of("§7Buy seeds for cash")));
        inv.setItem(HUB_SLOT_STORAGE, icon(Material.CHEST, "§eStorage",
                List.of("§7See and sell what's in farm storage")));

        int owned = st.plotsOwned();
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Balance: §a$" + st.wallet().balance());
        infoLore.add("§7Plots: §f" + owned + "§8/" + PlotPricing.MAX_PLOTS);
        if (PlotPricing.canBuy(owned)) {
            infoLore.add("§7Next plot: §f$" + PlotPricing.costFor(owned));
            infoLore.add("§8Right-click dirt/grass/coarse dirt with this hoe");
        } else {
            infoLore.add("§8Field is full.");
        }
        inv.setItem(HUB_SLOT_INFO, icon(Material.EMERALD, "§bFarm Info", infoLore));
    }

    // ---- Seed shop ----

    public void openSeedShop(Player player) {
        SeedShopHolder holder = new SeedShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Seed Shop");
        holder.inventory = inv;
        renderSeedShop(inv, holder);
        player.openInventory(inv);
    }

    private void renderSeedShop(Inventory inv, SeedShopHolder holder) {
        inv.clear();
        holder.slotCrops.clear();
        Crop[] crops = Crop.values();
        int[] slots = centeredSlots(crops.length);
        for (int i = 0; i < crops.length; i++) {
            Crop c = crops[i];
            List<String> lore = List.of(
                    "§7Cost: §f$" + c.seedCost(),
                    "§7Grows in §f" + c.growSeconds() + "s",
                    "§7Plantings per seed: §f" + c.plantings(),
                    "",
                    "§8Click to buy one"
            );
            inv.setItem(slots[i], icon(seedIconMaterial(c), "§e" + c.displayName() + " Seed", lore));
            holder.slotCrops.put(slots[i], c);
        }
        inv.setItem(BACK_SLOT, icon(Material.ARROW, "§7Back", List.of("§8Return to the hub")));
    }

    private static Material seedIconMaterial(Crop crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROT -> Material.CARROT;
        };
    }

    // ---- Storage ----

    public void openStorage(Player player) {
        StorageHolder holder = new StorageHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Farm Storage");
        holder.inventory = inv;
        renderStorage(player, inv, holder);
        player.openInventory(inv);
    }

    private void renderStorage(Player player, Inventory inv, StorageHolder holder) {
        inv.clear();
        holder.slotCrops.clear();
        FarmState st = farms.state(player.getUniqueId());
        List<Crop> held = new ArrayList<>();
        for (Crop c : Crop.values()) {
            if (st.storage().count(c) > 0) held.add(c);
        }

        if (held.isEmpty()) {
            inv.setItem(13, icon(Material.BARRIER, "§7Nothing in storage",
                    List.of("§8Harvest crops to fill this up")));
        } else {
            int[] slots = centeredSlots(held.size());
            for (int i = 0; i < held.size(); i++) {
                Crop c = held.get(i);
                int count = st.storage().count(c);
                List<String> lore = List.of(
                        "§7Quantity: §f" + count,
                        "§7Unit value: §f$" + c.rawValue(),
                        "§7Total: §a$" + ((long) count * c.rawValue()),
                        "",
                        "§8Click to sell all of these"
                );
                inv.setItem(slots[i], icon(seedIconMaterial(c),
                        "§e" + c.displayName() + " §8x" + count, lore));
                holder.slotCrops.put(slots[i], c);
            }
        }
        inv.setItem(BACK_SLOT, icon(Material.ARROW, "§7Back", List.of("§8Return to the hub")));
    }

    // ---- Plot purchase confirmation ----

    public void openPlotConfirm(Player player, Block block) {
        FarmState st = farms.state(player.getUniqueId());
        int owned = st.plotsOwned();

        PlotConfirmHolder holder = new PlotConfirmHolder();
        holder.world = block.getWorld().getName();
        holder.x = block.getX();
        holder.y = block.getY();
        holder.z = block.getZ();
        holder.expectedType = block.getType();

        Inventory inv = Bukkit.createInventory(holder, 27, "§8Buy a Plot");
        holder.inventory = inv;
        renderPlotConfirm(inv, owned, holder);
        player.openInventory(inv);
    }

    private void renderPlotConfirm(Inventory inv, int owned, PlotConfirmHolder holder) {
        inv.clear();
        boolean canBuy = PlotPricing.canBuy(owned);
        long price = canBuy ? PlotPricing.costFor(owned) : -1L;

        List<String> infoLore = new ArrayList<>(List.of(
                "§7Block: §f" + holder.world + " " + holder.x + "," + holder.y + "," + holder.z,
                "§7Plots owned: §f" + owned + "§8/" + PlotPricing.MAX_PLOTS
        ));
        infoLore.add(canBuy ? "§7Price: §f$" + price : "§8Field is full.");
        inv.setItem(PLOT_INFO_SLOT, icon(Material.MAP, "§bNew Plot", infoLore));

        if (canBuy) {
            inv.setItem(PLOT_CONFIRM_SLOT, icon(Material.LIME_DYE, "§aConfirm §8- $" + price,
                    List.of("§8Tills this block into farmland")));
        } else {
            inv.setItem(PLOT_CONFIRM_SLOT, icon(Material.GRAY_DYE, "§7Field is full",
                    List.of("§8Max plots reached")));
        }
        inv.setItem(PLOT_CANCEL_SLOT, icon(Material.RED_DYE, "§cCancel",
                List.of("§8Nothing is charged")));
    }

    private void confirmPurchase(Player player, PlotConfirmHolder holder) {
        FarmState st = farms.state(player.getUniqueId());
        int owned = st.plotsOwned();
        if (!PlotPricing.canBuy(owned)) {
            player.sendMessage(PREFIX + "Field is full.");
            player.closeInventory();
            return;
        }

        World world = Bukkit.getWorld(holder.world);
        if (world == null) {
            player.sendMessage(PREFIX + "That world isn't loaded anymore.");
            player.closeInventory();
            return;
        }
        Block block = world.getBlockAt(holder.x, holder.y, holder.z);
        if (block.getType() != holder.expectedType) {
            player.sendMessage(PREFIX + "That block changed since you opened the menu. Try again.");
            player.closeInventory();
            return;
        }

        long price = PlotPricing.costFor(owned);
        if (!st.wallet().spend(price)) {
            player.sendMessage(PREFIX + "Not enough money. Need §f$" + price + "§7.");
            player.closeInventory();
            return;
        }

        if (!st.incrementPlots()) {
            // Unreachable given the canBuy check above, but never keep the
            // player's money if the increment somehow fails anyway.
            st.wallet().credit(price);
            player.sendMessage(PREFIX + "Could not add the plot. Refunded.");
            player.closeInventory();
            return;
        }

        block.setType(Material.FARMLAND);
        player.sendMessage(PREFIX + "Bought a plot for §f$" + price + "§7. Plots: §f"
                + st.plotsOwned() + "§8/" + PlotPricing.MAX_PLOTS + "§7.");
        player.closeInventory();
    }

    // ---- shared ----

    /** Slots in the GUI's middle row, spreading outward from the centre. */
    private static int[] centeredSlots(int count) {
        int[] order = {13, 12, 14, 11, 15, 10, 16, 9, 17};
        int[] result = new int[count];
        System.arraycopy(order, 0, result, 0, Math.min(count, order.length));
        return result;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void giveSeed(Player player, Crop crop) {
        var leftover = player.getInventory().addItem(Items.seed(plugin, SeedStack.fresh(crop)));
        if (leftover.isEmpty()) {
            player.sendMessage(PREFIX + "One " + crop.displayName() + " seed — "
                    + crop.plantings() + " plantings in it.");
        } else {
            for (var stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
            player.sendMessage(PREFIX + "Your inventory was full, so it's on the ground.");
        }
    }

    // ---- events ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (!isOurs(rawHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (rawHolder instanceof HubHolder) {
            if (slot == HUB_SLOT_ORDERS) {
                board.open(player);
            } else if (slot == HUB_SLOT_SEEDS) {
                openSeedShop(player);
            } else if (slot == HUB_SLOT_STORAGE) {
                openStorage(player);
            }
        } else if (rawHolder instanceof SeedShopHolder holder) {
            if (slot == BACK_SLOT) {
                openHub(player);
                return;
            }
            Crop crop = holder.slotCrops.get(slot);
            if (crop == null) return;
            FarmState st = farms.state(player.getUniqueId());
            if (st.wallet().spend(crop.seedCost())) {
                giveSeed(player, crop);
            } else {
                player.sendMessage(PREFIX + "Not enough money for a " + crop.displayName()
                        + " seed. Need §f$" + crop.seedCost() + "§7.");
            }
        } else if (rawHolder instanceof StorageHolder holder) {
            if (slot == BACK_SLOT) {
                openHub(player);
                return;
            }
            Crop crop = holder.slotCrops.get(slot);
            if (crop == null) return;
            FarmState st = farms.state(player.getUniqueId());
            var sale = st.sell(crop);
            if (sale.isPresent()) {
                player.sendMessage(PREFIX + "Sold " + sale.get().quantity() + " × "
                        + sale.get().crop().displayName() + " for §f$" + sale.get().gross());
                // State changed, so redraw once. Not on a timer.
                renderStorage(player, event.getInventory(), holder);
            } else {
                player.sendMessage(PREFIX + "Nothing to sell.");
            }
        } else if (rawHolder instanceof PlotConfirmHolder holder) {
            if (slot == PLOT_CONFIRM_SLOT) {
                confirmPurchase(player, holder);
            } else if (slot == PLOT_CANCEL_SLOT) {
                player.closeInventory();
                player.sendMessage(PREFIX + "Cancelled. Nothing charged.");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isOurs(event.getInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    private static boolean isOurs(InventoryHolder holder) {
        return holder instanceof HubHolder || holder instanceof SeedShopHolder
                || holder instanceof StorageHolder || holder instanceof PlotConfirmHolder;
    }

    // ---- holders ----
    // Each view has its own InventoryHolder type so clicks route by identity,
    // never by comparing the (cosmetic-only) inventory title.

    private static final class HubHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class SeedShopHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Crop> slotCrops = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class StorageHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Crop> slotCrops = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class PlotConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private String world;
        private int x, y, z;
        private Material expectedType;
        @Override public Inventory getInventory() { return inventory; }
    }
}
