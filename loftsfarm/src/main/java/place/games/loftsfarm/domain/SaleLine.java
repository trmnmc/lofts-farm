package place.games.loftsfarm.domain;

/** What one crop's sale produced. */
public record SaleLine(Crop crop, int quantity, long gross) {}
