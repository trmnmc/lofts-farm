package place.games.loftsfarm.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled line format. No JSON library is available in this environment and
 * no downloads are permitted, so the format is deliberately trivial:
 *
 *   cash <balance>
 *   plots <plotsOwned>
 *   stock <cropId> <count>
 *   plant <world> <x> <y> <z> <cropId> <plantedAt> <readyAt>
 *
 * Spec §6 calls for SQLite. This is the throwaway-slice stand-in behind the same
 * seam; swapping to SQLite replaces this class only.
 */
public final class StateCodec {

    private StateCodec() {}

    private static List<String> lastSkipped = new ArrayList<>();

    /**
     * Return a copy of lines that were skipped during the most recent read()
     * due to parse errors, unknown crop ids, or truncation.
     */
    public static List<String> lastSkipped() {
        return new ArrayList<>(lastSkipped);
    }

    public static String write(FarmState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("cash ").append(state.wallet().balance()).append('\n');
        sb.append("plots ").append(state.plotsOwned()).append('\n');
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
        lastSkipped.clear();
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
            try {
                switch (p[0]) {
                    case "plots" -> state.setPlotsOwned(Integer.parseInt(p[1]));
                    case "stock" -> {
                        Crop c = Crop.byId(p[1]);
                        if (c != null) state.storage().add(c, Integer.parseInt(p[2]));
                    }
                    case "plant" -> {
                        // Parse the six fixed trailing fields from the end: <x> <y> <z> <cropId> <plantedAt> <readyAt>
                        // Everything between index 1 and (length - 6) is the world name (may contain spaces).
                        if (p.length < 8) {
                            lastSkipped.add(line);
                            continue;
                        }
                        int x = Integer.parseInt(p[p.length - 6]);
                        int y = Integer.parseInt(p[p.length - 5]);
                        int z = Integer.parseInt(p[p.length - 4]);
                        String cropId = p[p.length - 3];
                        long plantedAt = Long.parseLong(p[p.length - 2]);
                        long readyAt = Long.parseLong(p[p.length - 1]);

                        // Reconstruct world name from all tokens between 1 and (length - 6).
                        StringBuilder worldBuilder = new StringBuilder();
                        for (int i = 1; i < p.length - 6; i++) {
                            if (i > 1) worldBuilder.append(' ');
                            worldBuilder.append(p[i]);
                        }
                        String world = worldBuilder.toString();

                        Crop c = Crop.byId(cropId);
                        if (c != null) {
                            state.put(new PlantRecord(world, x, y, z, c, plantedAt, readyAt));
                        }
                    }
                    default -> { /* cash handled above; unknown lines ignored */ }
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                lastSkipped.add(line);
            }
        }
        return state;
    }
}
