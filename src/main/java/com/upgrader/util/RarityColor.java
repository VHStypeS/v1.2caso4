package com.upgrader.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Chance (percent) -> colour: rare = red, middle = yellow, common = green. Used by GUI and chat. */
public final class RarityColor {
    private static final int RED = 0xFF3B3B;
    private static final int YELLOW = 0xFFD83B;
    private static final int GREEN = 0x3BFF5A;

    private RarityColor() {}

    /** 0.01% -> red ... 1% -> yellow ... 50%+ -> green (logarithmic scale). */
    public static int color(double percent) {
        double p = Math.max(percent, 0.001);
        double t = (Math.log10(p) + 2.0) / (Math.log10(50.0) + 2.0);
        t = Math.max(0.0, Math.min(1.0, t));
        return t < 0.5 ? lerp(RED, YELLOW, t / 0.5) : lerp(YELLOW, GREEN, (t - 0.5) / 0.5);
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** "12.5", "0.1", "0.025" (no trailing zeros). */
    public static String fmt(double percent) {
        int scale = percent >= 10 ? 1 : percent >= 1 ? 2 : 4;
        return BigDecimal.valueOf(percent).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
