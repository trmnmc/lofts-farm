package place.games.loftsfarm.domain;

/** One good demanded by an order. A single-good order is a one-line order. */
public record OrderLine(Crop crop, int qty, long unitPrice) {

    public OrderLine {
        if (crop == null) throw new IllegalArgumentException("crop required");
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        if (unitPrice < 0L) throw new IllegalArgumentException("unitPrice cannot be negative");
    }

    public long lineValue() {
        return qty * unitPrice;
    }
}
