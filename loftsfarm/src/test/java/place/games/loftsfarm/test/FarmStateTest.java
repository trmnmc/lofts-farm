package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.SaleLine;

import java.util.List;

public final class FarmStateTest {
    public static void run() {
        FarmState empty = new FarmState(250L);
        List<SaleLine> soldNothing = empty.sellAll();
        Check.isTrue("selling empty storage returns an empty list", soldNothing.isEmpty());
        Check.eq("selling empty storage leaves balance untouched", empty.wallet().balance(), 250L);

        FarmState oneCrop = new FarmState(250L);
        oneCrop.storage().add(Crop.WHEAT, 4);
        List<SaleLine> soldWheat = oneCrop.sellAll();
        Check.eq("selling one crop reports exactly one line", soldWheat.size(), 1);
        SaleLine wheatLine = soldWheat.get(0);
        Check.eq("reported crop is the one sold", wheatLine.crop(), Crop.WHEAT);
        Check.eq("reported quantity matches what was sold", wheatLine.quantity(), 4);
        Check.eq("selling one crop credits count times rawValue",
                wheatLine.gross(), 4L * Crop.WHEAT.rawValue());
        Check.eq("wallet credited by the sale",
                oneCrop.wallet().balance(), 250L + 4L * Crop.WHEAT.rawValue());
        Check.eq("sold crop's storage is emptied", oneCrop.storage().count(Crop.WHEAT), 0);
        Check.isFalse("a crop with zero stock never appears in the returned list",
                soldWheat.stream().anyMatch(l -> l.crop() == Crop.CARROT));

        // Add in reverse-of-enum order (CARROT before WHEAT) to prove the
        // returned order is Crop enum order, not insertion order.
        FarmState twoCrops = new FarmState(0L);
        twoCrops.storage().add(Crop.CARROT, 3);
        twoCrops.storage().add(Crop.WHEAT, 2);
        List<SaleLine> soldBoth = twoCrops.sellAll();
        Check.eq("selling two crops reports both", soldBoth.size(), 2);
        Check.eq("results come back in Crop enum order regardless of insertion order",
                soldBoth.get(0).crop(), Crop.WHEAT);
        Check.eq("results come back in Crop enum order regardless of insertion order",
                soldBoth.get(1).crop(), Crop.CARROT);
        Check.eq("wheat quantity reported correctly", soldBoth.get(0).quantity(), 2);
        Check.eq("carrot quantity reported correctly", soldBoth.get(1).quantity(), 3);
        Check.eq("wheat gross is count times rawValue",
                soldBoth.get(0).gross(), 2L * Crop.WHEAT.rawValue());
        Check.eq("carrot gross is count times rawValue",
                soldBoth.get(1).gross(), 3L * Crop.CARROT.rawValue());
        long expectedTotal = 2L * Crop.WHEAT.rawValue() + 3L * Crop.CARROT.rawValue();
        Check.eq("wallet credited by both sales", twoCrops.wallet().balance(), expectedTotal);
        Check.eq("both crops' storage emptied afterward (wheat)", twoCrops.storage().count(Crop.WHEAT), 0);
        Check.eq("both crops' storage emptied afterward (carrot)", twoCrops.storage().count(Crop.CARROT), 0);

        List<SaleLine> soldAgain = twoCrops.sellAll();
        Check.isTrue("selling again with now-empty storage returns an empty list", soldAgain.isEmpty());
        Check.eq("selling again does not touch the balance", twoCrops.wallet().balance(), expectedTotal);
    }
}
