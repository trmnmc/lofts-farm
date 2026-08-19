package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.Crop;
import place.games.loftsfarm.domain.FarmState;
import place.games.loftsfarm.domain.PlantRecord;
import place.games.loftsfarm.domain.StateCodec;

public final class StateCodecTest {
    public static void run() {
        FarmState st = new FarmState(250L);
        st.storage().add(Crop.WHEAT, 4);
        st.storage().add(Crop.CARROT, 1);
        st.wallet().credit(75L);
        st.put(new PlantRecord("flat", 10, 64, -3, Crop.WHEAT, 1_000L, 31_000L));
        st.put(new PlantRecord("flat", 11, 64, -3, Crop.CARROT, 2_000L, 47_000L));

        Check.eq("key format", st.plants().get("flat:10:64:-3").key(), "flat:10:64:-3");

        String text = StateCodec.write(st);
        FarmState back = StateCodec.read(text);

        Check.eq("balance round-trips", back.wallet().balance(), 325L);
        Check.eq("wheat stock round-trips", back.storage().count(Crop.WHEAT), 4);
        Check.eq("carrot stock round-trips", back.storage().count(Crop.CARROT), 1);
        Check.eq("plant count round-trips", back.plants().size(), 2);

        PlantRecord p = back.plants().get("flat:10:64:-3");
        Check.eq("plant crop round-trips", p.crop(), Crop.WHEAT);
        Check.eq("plantedAt round-trips", p.plantedAt(), 1_000L);
        Check.eq("readyAt round-trips", p.readyAt(), 31_000L);
        Check.eq("negative coordinate round-trips", p.z(), -3);

        // Ripeness must survive a restart because it is derived from a timestamp.
        Check.isTrue("crop is ripe against a later clock", p.clock().ripe(31_000L));
        Check.isFalse("and not ripe against an earlier one", p.clock().ripe(30_999L));

        Check.eq("removing returns the record", st.remove("flat:11:64:-3").crop(), Crop.CARROT);
        Check.eq("removal shrinks the map", st.plants().size(), 1);
        Check.eq("removing an absent key is null", st.remove("nope"), null);

        FarmState empty = StateCodec.read(StateCodec.write(new FarmState(0L)));
        Check.eq("empty state round-trips", empty.plants().size(), 0);
        Check.eq("zero balance round-trips", empty.wallet().balance(), 0L);
    }
}
