package place.games.loftsfarm.domain;

/**
 * Pricing for buying additional farm plots with the Smart Hoe.
 *
 * Cost grows 1.75x per plot owned beyond the starting four, so early plots
 * are cheap and the field caps out at MAX_PLOTS no matter how much money a
 * player has. Numbers come from the browser reference build, not invented.
 */
public final class PlotPricing {
    public static final int START_PLOTS = 4;
    public static final int MAX_PLOTS = 12;

    private static final long BASE_COST = 400L;
    private static final double GROWTH = 1.75d;

    private PlotPricing() {}

    /** Price of the next plot, given how many the player already owns. */
    public static long costFor(int plotsOwned) {
        return Math.round(BASE_COST * Math.pow(GROWTH, plotsOwned - START_PLOTS));
    }

    public static boolean canBuy(int plotsOwned) {
        return plotsOwned < MAX_PLOTS;
    }
}
