package place.games.loftsfarm.test;

import java.util.List;
import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.Fertiliser;
import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.OrderLine;
import place.games.loftsfarm.domain.Storage;

public final class OrderTest {
    public static void run() {
        // Fertiliser is priced off crop value and how much time is left, so it is
        // always net-positive to use (P5). Wheat raw value 20, factor 0.26.
        Check.eq("full wheat cycle costs 5", Fertiliser.costFor(Crop.WHEAT, 30_000L), 5L);
        Check.eq("half a wheat cycle costs 4 (floored at minimum)",
                Fertiliser.costFor(Crop.WHEAT, 15_000L), 4L);
        Check.eq("a ripe crop costs the minimum", Fertiliser.costFor(Crop.WHEAT, 0L), 4L);
        Check.eq("full carrot cycle costs 8", Fertiliser.costFor(Crop.CARROT, 45_000L), 8L);
        Check.isTrue("fertilising is always cheaper than the crop is worth",
                Fertiliser.costFor(Crop.CARROT, 45_000L) < Crop.CARROT.rawValue());

        Order o = Order.single("k1", Crop.WHEAT, 3, 23L, 100_000L);
        Check.eq("one line", o.lines().size(), 1);
        Check.eq("pay is qty times unit", o.pay(), 69L);
        Check.isFalse("not expired before due", o.expired(99_999L));
        Check.isTrue("expired on due", o.expired(100_000L));

        Storage s = new Storage();
        Check.isFalse("empty storage does not satisfy", o.satisfiedBy(s));
        Check.eq("holds none", o.heldFor(s, 0), 0);

        s.add(Crop.WHEAT, 2);
        Check.isFalse("partial stock does not satisfy", o.satisfiedBy(s));
        Check.eq("holds two of three", o.heldFor(s, 0), 2);
        Check.isFalse("cannot deliver while short", o.deliver(s));
        Check.eq("failed delivery consumed nothing", s.count(Crop.WHEAT), 2);

        s.add(Crop.WHEAT, 5);
        Check.isTrue("enough stock satisfies", o.satisfiedBy(s));
        Check.eq("tally caps at required", o.heldFor(s, 0), 3);
        Check.isTrue("delivery succeeds", o.deliver(s));
        Check.eq("delivery consumed exactly the quantity", s.count(Crop.WHEAT), 4);

        // Multi-line order: test atomicity of deliver() with wheat and carrot
        Order multi = new Order("m1", List.of(
                new OrderLine(Crop.WHEAT, 2, 10L),
                new OrderLine(Crop.CARROT, 3, 15L)
        ), 200_000L);

        Check.eq("two lines", multi.lines().size(), 2);
        Check.eq("pay is sum of both lines", multi.pay(), 65L);

        Storage s2 = new Storage();
        s2.add(Crop.WHEAT, 2);
        // Line 1 (wheat) satisfied, line 2 (carrot) short
        Check.isFalse("unsatisfied multi-line order", multi.satisfiedBy(s2));
        Check.isFalse("deliver fails when any line short", multi.deliver(s2));
        // Atomicity: neither line was consumed
        Check.eq("wheat unchanged after failed delivery", s2.count(Crop.WHEAT), 2);
        Check.eq("carrot unchanged after failed delivery", s2.count(Crop.CARROT), 0);
        // Per-line tally in partial state
        Check.eq("heldFor line 0 (wheat satisfied)", multi.heldFor(s2, 0), 2);
        Check.eq("heldFor line 1 (carrot short)", multi.heldFor(s2, 1), 0);

        // Top up carrot, now deliver should succeed
        s2.add(Crop.CARROT, 3);
        Check.isTrue("satisfied multi-line order", multi.satisfiedBy(s2));
        Check.isTrue("deliver succeeds when all lines met", multi.deliver(s2));
        Check.eq("wheat consumed exactly", s2.count(Crop.WHEAT), 0);
        Check.eq("carrot consumed exactly", s2.count(Crop.CARROT), 0);
    }
}
