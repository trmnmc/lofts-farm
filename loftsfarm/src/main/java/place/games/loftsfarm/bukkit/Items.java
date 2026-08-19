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

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cropKey(plugin), PersistentDataType.STRING, stack.crop().id());
        pdc.set(usesKey(plugin), PersistentDataType.INTEGER, stack.remaining());

        // Drive the vanilla damage bar off remaining plantings so wear is visible.
        if (meta instanceof Damageable dmg) {
            int max = item.getType().getMaxDurability();
            if (max > 0) dmg.setDamage(stack.damageFor(max));
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
        return new SeedStack(crop, Math.min(uses, crop.plantings()));
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
}
