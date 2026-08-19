package place.games.loftsfarm.test;

import java.util.Random;
import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.Order;
import place.games.loftsfarm.domain.OrderFactory;

public final class OrderFactoryTest {
    public static void run() {
        long now = 1_000_000L;
        Random random = new Random(42L);

        Order wheat = OrderFactory.create(Crop.WHEAT, now, random);
        Check.eq("wheat unit price is rawValue x1.15 rounded",
                wheat.lines().get(0).unitPrice(), 23L);
        Check.isTrue("wheat fuse strictly exceeds one full grow cycle",
                wheat.dueAt() - now > Crop.WHEAT.growSeconds() * 1000L);
        Check.eq("wheat pay is qty times unit", wheat.pay(),
                (long) wheat.lines().get(0).qty() * wheat.lines().get(0).unitPrice());

        Order carrot = OrderFactory.create(Crop.CARROT, now, random);
        Check.eq("carrot unit price is rawValue x1.15 rounded",
                carrot.lines().get(0).unitPrice(), 35L);
        Check.isTrue("carrot fuse strictly exceeds one full grow cycle",
                carrot.dueAt() - now > Crop.CARROT.growSeconds() * 1000L);
        Check.eq("carrot pay is qty times unit", carrot.pay(),
                (long) carrot.lines().get(0).qty() * carrot.lines().get(0).unitPrice());
    }
}
