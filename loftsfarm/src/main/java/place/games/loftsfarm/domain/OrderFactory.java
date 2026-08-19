package place.games.loftsfarm.domain;

import java.util.Random;

/**
 * Builds the single procedurally-generated order the board offers. This is
 * pure policy — no Bukkit types touch it, so it is directly testable,
 * unlike its previous home in the bukkit package.
 *
 * A missed order costs the payout and nothing else (P5); there is no penalty
 * path here. The fuse is deliberately generous: it always comfortably clears
 * a full grow cycle, or accepting the order would be a trap.
 */
public final class OrderFactory {
    private OrderFactory() {}

    public static Order create(Crop crop, long now, Random random) {
        int qty = 3;
        long unit = Math.round(crop.rawValue() * 1.15d);
        long due = now + (long) (crop.growSeconds() * 1000L * 1.25d) + 45_000L;
        String id = "k" + random.nextInt(1_000_000);
        return Order.single(id, crop, qty, unit, due);
    }
}
