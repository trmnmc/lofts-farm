package place.games.loftsfarm.bukkit;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.SeedStack;

public final class FarmCommand implements CommandExecutor {
    private static final String P = "\u00A78[\u00A7c!\u00A78] \u00A77";

    private final Plugin plugin;
    private final FarmService farms;

    public FarmCommand(Plugin plugin, FarmService farms) {
        this.plugin = plugin;
        this.farms = farms;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        FarmState st = farms.state(player.getUniqueId());

        switch (sub) {
            case "give" -> {
                String what = args.length > 1 ? args[1].toLowerCase() : "wheat";
                if (what.startsWith("fert")) {
                    var fert = Items.fertiliser(plugin);
                    fert.setAmount(16);
                    player.getInventory().addItem(fert);
                    player.sendMessage(P + "16 fertiliser.");
                } else {
                    Crop crop = Crop.byId(what);
                    if (crop == null) {
                        player.sendMessage(P + "Unknown crop. Try wheat, carrot, or fert.");
                        return true;
                    }
                    player.getInventory().addItem(Items.seed(plugin, SeedStack.fresh(crop)));
                    player.sendMessage(P + "One " + crop.displayName()
                            + " seed \u2014 " + crop.plantings() + " plantings in it.");
                }
            }
            case "balance" -> player.sendMessage(P + "Balance: \u00A7f$" + st.wallet().balance());
            case "sell" -> {
                long total = 0L;
                for (Crop c : Crop.values()) {
                    int n = st.storage().count(c);
                    if (n > 0 && st.storage().take(c, n)) {
                        long gross = n * c.rawValue();
                        st.wallet().credit(gross);
                        total += gross;
                        player.sendMessage(P + "Sold " + n + " \u00D7 " + c.displayName()
                                + " for \u00A7f$" + gross);
                    }
                }
                if (total == 0L) player.sendMessage(P + "Nothing in farm storage.");
                else player.sendMessage(P + "Total \u00A7f$" + total
                        + "\u00A77. Balance \u00A7f$" + st.wallet().balance());
            }
            case "setup" -> {
                if (!player.isOp()) {
                    player.sendMessage(P + "Operators only.");
                    return true;
                }
                var base = player.getLocation().getBlock();
                int made = 0;
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        var b = base.getRelative(dx + 1, -1, dz);
                        b.setType(Material.FARMLAND);
                        b.getRelative(0, 1, 0).setType(Material.AIR);
                        made++;
                    }
                }
                player.sendMessage(P + "Laid " + made + " farmland blocks. Sow with a seed in hand.");
            }
            default -> {
                player.sendMessage(P + "\u00A7eLoftsFarm slice");
                player.sendMessage(P + "/farm setup \u00A78- lay a test plot (op)");
                player.sendMessage(P + "/farm give <wheat|carrot|fert>");
                player.sendMessage(P + "/farm balance");
                player.sendMessage(P + "/farm sell \u00A78- sell everything in farm storage");
                player.sendMessage(P + "\u00A78Right-click farmland to sow, a ripe crop to harvest.");
            }
        }
        return true;
    }
}
