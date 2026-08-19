package place.games.loftsfarm.domain;

/**
 * Hand-rolled line format. No JSON library is available in this environment and
 * no downloads are permitted, so the format is deliberately trivial:
 *
 *   cash <balance>
 *   stock <cropId> <count>
 *   plant <world> <x> <y> <z> <cropId> <plantedAt> <readyAt>
 *
 * Spec §6 calls for SQLite. This is the throwaway-slice stand-in behind the same
 * seam; swapping to SQLite replaces this class only.
 */
public final class StateCodec {

    private StateCodec() {}

    public static String write(FarmState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("cash ").append(state.wallet().balance()).append('\n');
        state.storage().snapshot().forEach((crop, n) -> {
            if (n > 0) sb.append("stock ").append(crop.id()).append(' ').append(n).append('\n');
        });
        for (PlantRecord p : state.plants().values()) {
            sb.append("plant ").append(p.world()).append(' ')
              .append(p.x()).append(' ').append(p.y()).append(' ').append(p.z()).append(' ')
              .append(p.crop().id()).append(' ')
              .append(p.plantedAt()).append(' ').append(p.readyAt()).append('\n');
        }
        return sb.toString();
    }

    public static FarmState read(String text) {
        long cash = 0L;
        // Balance must be known before the state is constructed.
        for (String line : text.split("\n")) {
            if (line.startsWith("cash ")) {
                cash = Long.parseLong(line.substring(5).trim());
                break;
            }
        }
        FarmState state = new FarmState(cash);

        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split(" ");
            switch (p[0]) {
                case "stock" -> {
                    Crop c = Crop.byId(p[1]);
                    if (c != null) state.storage().add(c, Integer.parseInt(p[2]));
                }
                case "plant" -> {
                    Crop c = Crop.byId(p[5]);
                    if (c != null) {
                        state.put(new PlantRecord(p[1],
                                Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                                c, Long.parseLong(p[6]), Long.parseLong(p[7])));
                    }
                }
                default -> { /* cash handled above; unknown lines ignored */ }
            }
        }
        return state;
    }
}
