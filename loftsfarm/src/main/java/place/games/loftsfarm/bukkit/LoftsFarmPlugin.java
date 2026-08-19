package place.games.loftsfarm.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public final class LoftsFarmPlugin extends JavaPlugin {

    private FarmService farms;
    private GrowthScheduler scheduler;

    @Override
    public void onEnable() {
        this.scheduler = new GrowthScheduler(this);
        this.farms = new FarmService(this, scheduler);
        farms.load();
        OrderService orders = new OrderService(farms);
        BoardGui board = new BoardGui(farms, orders);
        HoeGui hoeGui = new HoeGui(this, farms, board);
        getServer().getPluginManager().registerEvents(new FarmListener(this, farms, hoeGui), this);
        getServer().getPluginManager().registerEvents(board, this);
        getServer().getPluginManager().registerEvents(hoeGui, this);
        var cmd = getCommand("farm");
        if (cmd != null) cmd.setExecutor(new FarmCommand(this, farms, board));
        getLogger().info("LoftsFarm v2 slice enabled. v1 economy untouched.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.cancelAll();
        if (farms != null) farms.save();
        getLogger().info("LoftsFarm v2 slice disabled.");
    }

    public FarmService farms() { return farms; }
    public GrowthScheduler scheduler() { return scheduler; }
}
