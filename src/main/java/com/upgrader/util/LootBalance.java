package com.upgrader.util;

import java.util.Arrays;

/**
 * Splits the drop chances of a batch of random items:
 *  - by item COUNT: 33% of the items are the most common class, 10% are LEGENDARY, the rest go in between
 *    (ordered by the unified rating: the easier / weaker item = the more common class);
 *  - by DROP CHANCE: every class gets a fixed share of the batch (BUDGET), so commons really drop most of the time
 *    and legendaries are a rare, announced event. Inside a class the weaker/cheaper item still drops more often.
 * Change the constants below to rebalance everything.
 */
public final class LootBalance {
    public static final double SHARE_COMMON = 0.33;
    public static final double SHARE_RARE = 0.20;
    public static final double SHARE_EPIC = 0.13;
    public static final double SHARE_LEGENDARY = 0.10;
    // uncommon = everything that is left (about 24%)

    /** Percent of the batch's total chance per class: common, uncommon, rare, epic, legendary. */
    public static final double[] BUDGET = {55, 24, 13, 6, 2};

    public record Result(LootRarity[] rarity, double[] percent) {}

    private LootBalance() {}

    private static double weight(double rating) {
        return 100.0 / Math.pow(Math.max(0.5, rating), 1.5);
    }

    /** @return rarity and chance (percent, sum = 100) of every item, same order as the ratings. */
    public static Result distribute(double[] rating) {
        int n = rating.length;
        LootRarity[] rar = new LootRarity[n];
        double[] pct = new double[n];
        if (n == 0) return new Result(rar, pct);

        if (n < 4) { // too few items for classes: absolute rarity, chance by rating
            double sum = 0;
            for (int i = 0; i < n; i++) {
                rar[i] = LootRarity.byRating(rating[i]);
                pct[i] = weight(rating[i]);
                sum += pct[i];
            }
            for (int i = 0; i < n; i++) pct[i] = pct[i] / sum * 100.0;
            return new Result(rar, pct);
        }

        Integer[] ord = new Integer[n];
        for (int i = 0; i < n; i++) ord[i] = i;
        Arrays.sort(ord, (a, b) -> Double.compare(rating[a], rating[b]));

        int cC = Math.max(1, (int) Math.round(n * SHARE_COMMON));
        int cL = Math.max(1, (int) Math.round(n * SHARE_LEGENDARY));
        int cE = (int) Math.round(n * SHARE_EPIC);
        int cR = (int) Math.round(n * SHARE_RARE);
        int cU = n - cC - cL - cE - cR;
        while (cU < 0) {
            if (cR > 0) cR--;
            else if (cE > 0) cE--;
            else break;
            cU++;
        }
        int[] counts = {cC, cU, cR, cE, cL};

        double[] classSum = new double[5];
        int pos = 0;
        for (int k = 0; k < 5; k++) {
            for (int j = 0; j < counts[k] && pos < n; j++, pos++) {
                int idx = ord[pos];
                rar[idx] = LootRarity.values()[k];
                pct[idx] = weight(rating[idx]);
                classSum[k] += pct[idx];
            }
        }
        double budgetSum = 0;
        for (int k = 0; k < 5; k++) if (classSum[k] > 0) budgetSum += BUDGET[k];
        for (int i = 0; i < n; i++) {
            int k = rar[i].ordinal();
            pct[i] = BUDGET[k] / budgetSum * 100.0 * pct[i] / classSum[k];
        }
        return new Result(rar, pct);
    }
}
