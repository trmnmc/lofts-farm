package place.games.loftsfarm.test;

import place.games.loftsfarm.domain.PlotPricing;

public final class PlotPricingTest {
    public static void run() {
        Check.eq("START_PLOTS is 4", PlotPricing.START_PLOTS, 4);
        Check.eq("MAX_PLOTS is 12", PlotPricing.MAX_PLOTS, 12);

        Check.eq("cost at 4 owned", PlotPricing.costFor(4), 400L);
        Check.eq("cost at 5 owned", PlotPricing.costFor(5), 700L);
        Check.eq("cost at 6 owned", PlotPricing.costFor(6), 1225L);
        Check.eq("cost at 7 owned", PlotPricing.costFor(7), 2144L);
        Check.eq("cost at 8 owned", PlotPricing.costFor(8), 3752L);
        Check.eq("cost at 9 owned", PlotPricing.costFor(9), 6565L);
        Check.eq("cost at 10 owned", PlotPricing.costFor(10), 11489L);
        Check.eq("cost at 11 owned", PlotPricing.costFor(11), 20106L);

        Check.isTrue("can buy below max", PlotPricing.canBuy(11));
        Check.isFalse("cannot buy at max", PlotPricing.canBuy(12));
    }
}
