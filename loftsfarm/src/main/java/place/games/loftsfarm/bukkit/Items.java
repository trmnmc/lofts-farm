package place.games.loftsfarm.bukkit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

import java.util.List;

/** Builds and reads the plugin's custom items. */
public final class Items {

    private Items() {}

    private static NamespacedKey cropKey(Plugin p) { return new NamespacedKey(p, "crop"); }
    private static NamespacedKey usesKey(Plugin p) { return new NamespacedKey(p, "uses"); }
    private static NamespacedKey fertKey(Plugin p) { return new NamespacedKey(p, "fertiliser"); }
    private static NamespacedKey hoeKey(Plugin p) { return new NamespacedKey(p, "smart_hoe"); }

    /** Vanilla material used to represent each crop's seed. */
    private static Material seedMaterial(Crop crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROT -> Material.CARROT;
        };
    }

    public static ItemStack seed(Plugin plugin, SeedStack stack) {
        ItemStack item = new ItemStack(seedMaterial(stack.crop()));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("\u00A7e" + stack.crop().displayName() + " Seed");
        meta.setLore(List.of(
                "\u00A77Plantings left: \u00A7f" + stack.remaining() + "\u00A78/" + stack.crop().plantings(),
                "\u00A78Grows in " + stack.crop().growSeconds() + "s",
                "\u00A78Right-click farmland to sow"
        ));

        // This item carries per-item mutable state (remaining plantings) in its
        // own meta. Two of these must never share a stack slot, or sowing one
        // decrements both — so it is pinned to a stack size of 1 at the source,
        // making every held.setItemMeta(...) write correct by construction.
        meta.setMaxStackSize(1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cropKey(plugin), PersistentDataType.STRING, stack.crop().id());
        pdc.set(usesKey(plugin), PersistentDataType.INTEGER, stack.remaining());

        // Drive the vanilla damage bar off remaining plantings so wear is visible.
        // WHEAT_SEEDS/CARROT report getMaxDurability() == 0, so the max_damage data
        // component is set explicitly (Paper 1.20.5+) to make the item damageable
        // at all; one durability point per planting.
        if (meta instanceof Damageable dmg) {
            int max = stack.crop().plantings();
            dmg.setMaxDamage(max);
            dmg.setDamage(stack.crop().plantings() - stack.remaining());
        }

        item.setItemMeta(meta);
        return item;
    }

    public static SeedStack readSeed(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(cropKey(plugin), PersistentDataType.STRING);
        Integer uses = pdc.get(usesKey(plugin), PersistentDataType.INTEGER);
        if (id == null || uses == null) return null;
        Crop crop = Crop.byId(id);
        if (crop == null) return null;
        // A tampered or corrupt PDC value must not be silently repaired into a
        // legitimate-looking seed: treat it the same as "not one of ours".
        if (uses < 1 || uses > crop.plantings()) return null;
        return new SeedStack(crop, uses);
    }

    public static ItemStack fertiliser(Plugin plugin) {
        ItemStack item = new ItemStack(Material.BONE_MEAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("\u00A7aFertiliser");
        meta.setLore(List.of(
                "\u00A77Right-click a growing crop",
                "\u00A77Halves the time it has left",
                "\u00A78Cost scales with time remaining"
        ));
        meta.getPersistentDataContainer().set(fertKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFertiliser(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(fertKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * The whole player-facing interface in one hotbar item: right-click opens
     * the menu, right-click on dirt/grass/coarse dirt buys a plot. Unbreakable
     * and capped at a stack of one, same reasoning as the seed item above --
     * there is no per-item state here, but there is also no reason a player
     * should ever be holding more than one.
     */
    public static ItemStack smartHoe(Plugin plugin) {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bSmart Hoe");
        meta.setLore(List.of(
                "§7Right-click: open the farm menu",
                "§7Right-click dirt, grass or coarse dirt: buy a plot",
                "§8Orders, seeds and storage all live in here"
        ));
        meta.setUnbreakable(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(hoeKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSmartHoe(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(hoeKey(plugin), PersistentDataType.BYTE);
    }
}
