package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;

import java.util.Map;

public final class FarmStateTest {
    public static void run() {
        FarmState empty = new FarmState(250L);
        Map<Crop, Long> soldNothing = empty.sellAll();
        Check.isTrue("selling empty storage returns an empty map", soldNothing.isEmpty());
        Check.eq("selling empty storage leaves balance untouched", empty.wallet().balance(), 250L);

        FarmState oneCrop = new FarmState(250L);
        oneCrop.storage().add(Crop.WHEAT, 4);
        Map<Crop, Long> soldWheat = oneCrop.sellAll();
        Check.eq("selling one crop reports exactly one entry", soldWheat.size(), 1);
        Check.eq("selling one crop credits count times rawValue",
                soldWheat.get(Crop.WHEAT), 4L * Crop.WHEAT.rawValue());
        Check.eq("wallet credited by the sale",
                oneCrop.wallet().balance(), 250L + 4L * Crop.WHEAT.rawValue());
        Check.eq("sold crop's storage is emptied", oneCrop.storage().count(Crop.WHEAT), 0);
        Check.isFalse("a crop with zero stock never appears in the returned map",
                soldWheat.containsKey(Crop.CARROT));

        FarmState twoCrops = new FarmState(0L);
        twoCrops.storage().add(Crop.WHEAT, 2);
        twoCrops.storage().add(Crop.CARROT, 3);
        Map<Crop, Long> soldBoth = twoCrops.sellAll();
        Check.eq("selling two crops reports both", soldBoth.size(), 2);
        Check.eq("wheat gross is count times rawValue",
                soldBoth.get(Crop.WHEAT), 2L * Crop.WHEAT.rawValue());
        Check.eq("carrot gross is count times rawValue",
                soldBoth.get(Crop.CARROT), 3L * Crop.CARROT.rawValue());
        long expectedTotal = 2L * Crop.WHEAT.rawValue() + 3L * Crop.CARROT.rawValue();
        Check.eq("wallet credited by both sales", twoCrops.wallet().balance(), expectedTotal);
        Check.eq("both crops' storage emptied afterward (wheat)", twoCrops.storage().count(Crop.WHEAT), 0);
        Check.eq("both crops' storage emptied afterward (carrot)", twoCrops.storage().count(Crop.CARROT), 0);

        Map<Crop, Long> soldAgain = twoCrops.sellAll();
        Check.isTrue("selling again with now-empty storage returns an empty map", soldAgain.isEmpty());
        Check.eq("selling again does not touch the balance", twoCrops.wallet().balance(), expectedTotal);
    }
}
