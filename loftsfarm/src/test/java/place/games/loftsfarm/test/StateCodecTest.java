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

        // ---- Robustness tests for malformed input ----

        // Truncated plant line is skipped; the rest of the file loads; the skipped line is reported.
        String truncated = "cash 100\nplant flat 10 64\nplant sky 20 65 5 wheat 1000 31000\n";
        FarmState truncatedSt = StateCodec.read(truncated);
        Check.eq("truncated line: good plant loads", truncatedSt.plants().size(), 1);
        Check.eq("truncated line: skipped count", StateCodec.lastSkipped().size(), 1);
        Check.isTrue("truncated line: reported in skipped", StateCodec.lastSkipped().get(0).contains("plant flat 10 64"));

        // Non-numeric coordinate is skipped the same way.
        String badCoord = "cash 100\nplant badworld abc 64 5 wheat 1000 31000\nplant goodworld 21 65 5 wheat 1000 31000\n";
        FarmState badCoordSt = StateCodec.read(badCoord);
        Check.eq("bad coord: good plant loads", badCoordSt.plants().size(), 1);
        Check.eq("bad coord: skipped count", StateCodec.lastSkipped().size(), 1);

        // Unknown crop id is skipped (already handled).
        String unknownCrop = "cash 100\nplant flat 10 64 5 dragon 1000 31000\nplant sky 20 65 5 wheat 1000 31000\n";
        FarmState unknownCropSt = StateCodec.read(unknownCrop);
        Check.eq("unknown crop: other plants still load", unknownCropSt.plants().size(), 1);
        Check.isTrue("unknown crop: sky plant is present", unknownCropSt.plants().containsKey("sky:20:65:5"));

        // World name with space round-trips through write then read.
        FarmState withSpace = new FarmState(200L);
        withSpace.put(new PlantRecord("my world", 10, 64, 5, Crop.WHEAT, 1_000L, 31_000L));
        String spaceText = StateCodec.write(withSpace);
        FarmState spaceBack = StateCodec.read(spaceText);
        Check.eq("space in world: plant loads", spaceBack.plants().size(), 1);
        PlantRecord spaceP = spaceBack.plants().get("my world:10:64:5");
        Check.eq("space in world: key correct", spaceP.key(), "my world:10:64:5");
        Check.eq("space in world: world name intact", spaceP.world(), "my world");

        // File with good plant AND bad line loads only the good plant.
        String mixed = "cash 100\nplant good 10 64 5 wheat 1000 31000\nplant bad truncated\nplant another 20 65 5 carrot 2000 47000\n";
        FarmState mixedSt = StateCodec.read(mixed);
        Check.eq("mixed good/bad: good plants load", mixedSt.plants().size(), 2);
        Check.eq("mixed good/bad: skipped count", StateCodec.lastSkipped().size(), 1);
        Check.isTrue("mixed good/bad: contains first good plant", mixedSt.plants().containsKey("good:10:64:5"));
        Check.isTrue("mixed good/bad: contains second good plant", mixedSt.plants().containsKey("another:20:65:5"));
    }
}
