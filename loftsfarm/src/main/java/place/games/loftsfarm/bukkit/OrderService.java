package place.games.loftsfarm.bukkit;

import org.bukkit.entity.Player;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.OrderFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One live order per player for the slice.
 *
 * A missed order costs the payout and nothing else. There is deliberately no
 * penalty path in this class (P5) — if a future version wants one, the design
 * has changed and this comment should be deleted on purpose.
 *
 * Order policy (price, quantity, fuse) lives in the domain-layer
 * OrderFactory, not here — this class only picks a crop and holds the result.
 */
public final class OrderService {
    private final FarmService farms;
    private final Random random = new Random();
    private final Map<UUID, Order> live = new HashMap<>();

    public OrderService(FarmService farms) {
        this.farms = farms;
    }

    public Order currentFor(UUID id) {
        refreshIfExpired(id);
        return live.get(id);
    }

    public void refreshIfExpired(UUID id) {
        Order existing = live.get(id);
        long now = System.currentTimeMillis();
        if (existing != null && !existing.expired(now)) return;
        live.put(id, generate(now));
    }

    private Order generate(long now) {
        Crop crop = Crop.values()[random.nextInt(Crop.values().length)];
        return OrderFactory.create(crop, now, random);
    }

    public boolean deliver(UUID id, Player player) {
        Order order = currentFor(id);
        if (order == null) return false;
        FarmState st = farms.state(id);
        if (!order.deliver(st.storage())) return false;

        st.wallet().credit(order.pay());
        live.remove(id);
        refreshIfExpired(id);
        player.sendMessage("\u00A78[\u00A7c!\u00A78] \u00A77Delivered for \u00A7f$"
                + order.pay() + "\u00A77. Balance \u00A7f$" + st.wallet().balance());
        return true;
    }
}
