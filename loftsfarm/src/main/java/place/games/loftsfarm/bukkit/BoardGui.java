package place.games.loftsfarm.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.OrderLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The order board.
 *
 * Redrawn on state change only, never on a schedule. The browser build's worst
 * defect was rebuilding its panels four times a second, which destroyed any
 * click that landed mid-repaint. The same failure mode applies to inventory
 * GUIs and reads to players as lag.
 */
public final class BoardGui implements Listener {
    private static final String TITLE = "\u00A78Order Board";
    private static final int SLOT_ORDER = 11;
    private static final int SLOT_DELIVER = 15;

    private final FarmService farms;
    private final OrderService orders;

    public BoardGui(FarmService farms, OrderService orders) {
        this.farms = farms;
        this.orders = orders;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        render(player, inv);
        player.openInventory(inv);
    }

    private void render(Player player, Inventory inv) {
        inv.clear();
        Order order = orders.currentFor(player.getUniqueId());
        if (order == null) return;

        var storage = farms.state(player.getUniqueId()).storage();
        long secondsLeft = Math.max(0L, (order.dueAt() - System.currentTimeMillis()) / 1000L);

        OrderLine line = order.lines().get(0);
        int have = order.heldFor(storage, 0);

        List<String> lore = new ArrayList<>();
        lore.add("\u00A77Wants \u00A7f" + line.qty() + " \u00D7 " + line.crop().displayName());
        lore.add("\u00A77Pays \u00A7a$" + order.pay());
        lore.add("\u00A77You hold " + (have >= line.qty() ? "\u00A7a" : "\u00A7c")
                 + have + "\u00A77 of \u00A7f" + line.qty());
        lore.add("\u00A78" + secondsLeft + "s left");
        lore.add("");
        lore.add("\u00A78Miss it and you simply are not paid.");
        lore.add("\u00A78Nothing is ever taken from you.");

        inv.setItem(SLOT_ORDER, icon(Material.PAPER, "\u00A7eOrder \u00A78" + order.id(), lore));

        boolean ready = order.satisfiedBy(storage);
        inv.setItem(SLOT_DELIVER, icon(
                ready ? Material.LIME_DYE : Material.GRAY_DYE,
                ready ? "\u00A7aDeliver now" : "\u00A77Not enough yet",
                List.of(ready ? "\u00A78Click to hand it over"
                              : "\u00A78Harvest more, then come back")));
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() != SLOT_DELIVER) return;

        if (orders.deliver(player.getUniqueId(), player)) {
            // State changed, so redraw once. Not on a timer.
            render(player, event.getInventory());
        } else {
            player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Not enough in farm storage yet.");
        }
    }
}
