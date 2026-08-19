package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.SeedStack;

public final class SeedStackTest {
    public static void run() {
        SeedStack s = SeedStack.fresh(Crop.WHEAT);
        Check.eq("fresh wheat seed has 8 plantings", s.remaining(), 8);
        Check.isFalse("fresh seed is not spent", s.spent());
        Check.eq("remembers its crop", s.crop(), Crop.WHEAT);

        SeedStack after = s.usedOnce();
        Check.eq("one use consumed", after.remaining(), 7);
        Check.eq("original is unchanged (immutable)", s.remaining(), 8);

        SeedStack walk = SeedStack.fresh(Crop.CARROT);
        Check.eq("carrot starts at 7", walk.remaining(), 7);
        for (int i = 0; i < 7; i++) walk = walk.usedOnce();
        Check.eq("all uses consumed", walk.remaining(), 0);
        Check.isTrue("now spent", walk.spent());

        final SeedStack exhausted = walk;
        Check.throwsUp("using a spent seed is a bug, not a silent no-op",
                () -> exhausted.usedOnce());

        // Damage bar: full seed shows no damage, nearly-spent shows nearly full damage.
        SeedStack w = SeedStack.fresh(Crop.WHEAT);
        Check.eq("full seed has zero damage", w.damageFor(64), 0);
        Check.eq("half-used seed is half damaged", w.usedOnce().usedOnce()
                .usedOnce().usedOnce().damageFor(64), 32);

        SeedStack one = SeedStack.fresh(Crop.WHEAT);
        for (int i = 0; i < 7; i++) one = one.usedOnce();
        Check.eq("one planting left shows 7/8 damage", one.damageFor(64), 56);
        Check.isFalse("one planting left is not spent", one.spent());
    }
}
