package place.games.loftsfarm.domain;

import java.util.List;

/**
 * An order is a list of lines. A single-good order is simply a one-line order,
 * so there is one code path rather than two.
 *
 * All-or-nothing: an order pays only when every line is satisfied. Missing one
 * costs the payout and nothing else — there is no penalty path here (P5).
 */
public final class Order {
    private final String id;
    private final List<OrderLine> lines;
    private final long dueAt;

    public Order(String id, List<OrderLine> lines, long dueAt) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id required");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("at least one line");
        this.id = id;
        this.lines = List.copyOf(lines);
        this.dueAt = dueAt;
    }

    public static Order single(String id, Crop crop, int qty, long unitPrice, long dueAt) {
        return new Order(id, List.of(new OrderLine(crop, qty, unitPrice)), dueAt);
    }

    public String id() { return id; }
    public List<OrderLine> lines() { return lines; }
    public long dueAt() { return dueAt; }

    public long pay() {
        long total = 0L;
        for (OrderLine l : lines) total += l.lineValue();
        return total;
    }

    public boolean expired(long nowMs) {
        return nowMs >= dueAt;
    }

    /** How much of one line the player holds, capped at what the line requires. */
    public int heldFor(Storage storage, int lineIdx) {
        OrderLine l = lines.get(lineIdx);
        return Math.min(storage.count(l.crop()), l.qty());
    }

    public boolean satisfiedBy(Storage storage) {
        for (OrderLine l : lines) {
            if (storage.count(l.crop()) < l.qty()) return false;
        }
        return true;
    }

    /**
     * Consumes the goods if every line can be met. Consumes nothing otherwise,
     * so a failed delivery can never partially raid storage.
     */
    public boolean deliver(Storage storage) {
        if (!satisfiedBy(storage)) return false;
        for (OrderLine l : lines) storage.take(l.crop(), l.qty());
        return true;
    }
}
