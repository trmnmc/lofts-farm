package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.Storage;
import place.games.loftsfarm.domain.Wallet;

public final class WalletTest {
    public static void run() {
        Wallet w = new Wallet(250L);
        Check.eq("starts at 250", w.balance(), 250L);

        w.credit(50L);
        Check.eq("credit adds", w.balance(), 300L);

        Check.isTrue("affordable spend succeeds", w.spend(100L));
        Check.eq("balance after spend", w.balance(), 200L);

        Check.isFalse("unaffordable spend refused", w.spend(999L));
        Check.eq("refused spend changed nothing", w.balance(), 200L);

        Check.isTrue("spending the exact balance is allowed", w.spend(200L));
        Check.eq("can reach zero", w.balance(), 0L);
        Check.isFalse("cannot spend from zero", w.spend(1L));
        Check.eq("still zero", w.balance(), 0L);

        // P5: nothing may ever reduce a balance below zero, by any route.
        Check.throwsUp("negative credit is rejected", () -> w.credit(-5L));
        Check.throwsUp("negative spend is rejected", () -> w.spend(-5L));
        Check.eq("balance survived the rejections", w.balance(), 0L);

        Storage s = new Storage();
        Check.eq("empty storage counts zero", s.count(Crop.WHEAT), 0);

        s.add(Crop.WHEAT, 3);
        s.add(Crop.WHEAT, 2);
        Check.eq("adds accumulate", s.count(Crop.WHEAT), 5);
        Check.eq("other crops unaffected", s.count(Crop.CARROT), 0);

        Check.isFalse("taking more than held is refused", s.take(Crop.WHEAT, 6));
        Check.eq("refused take changed nothing", s.count(Crop.WHEAT), 5);

        Check.isTrue("taking exactly what is held works", s.take(Crop.WHEAT, 5));
        Check.eq("storage empties", s.count(Crop.WHEAT), 0);

        Check.throwsUp("negative add rejected", () -> s.add(Crop.WHEAT, -1));
        Check.throwsUp("negative take rejected", () -> s.take(Crop.WHEAT, -1));
    }
}
