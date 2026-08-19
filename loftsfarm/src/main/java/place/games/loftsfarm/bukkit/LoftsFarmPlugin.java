package place.games.loftsfarm.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public final class LoftsFarmPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("LoftsFarm v2 slice enabled. v1 economy untouched.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LoftsFarm v2 slice disabled.");
    }
}
