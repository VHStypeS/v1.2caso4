package com.upgrader.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared drawing helpers: animated backdrop, panels, glow, scaled text/items. */
public final class Ui {
    private Ui() {}

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hash(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    /** Dark gradient with slowly rising twinkling dust in the accent colour. */
    public static void backdrop(GuiGraphics g, int w, int h, int accent) {
        g.fillGradient(0, 0, w, h, 0xF004040B, 0xF0160B2A);
        g.fillGradient(0, h - h / 3, w, h, 0x00000000, argb(0x28, accent));
        long t = Util.getMillis();
        for (int i = 0; i < 56; i++) {
            int hx = hash(i);
            double fx = (hx & 1023) / 1023.0;
            double fy = ((hx >> 10) & 1023) / 1023.0;
            double sp = 0.006 + ((hx >> 20) & 7) * 0.002;
            int x = (int) (fx * w);
            int y = (int) ((fy * h - t * sp) % h);
            if (y < 0) y += h;
            int a = (int) (70 + 60 * Math.sin(t / 500.0 + i));
            int s = (hx & 0x4000) != 0 ? 2 : 1;
            g.fill(x, y, x + s, y + s, argb(a, (i & 1) == 0 ? 0xFFFFFF : accent));
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x55000000);
        g.fillGradient(x, y, x + w, y + h, 0xF01A1A34, 0xF00A0A18);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 24, argb(0x4A, accent), 0x00000000);
        g.renderOutline(x, y, w, h, argb(0xB0, accent));
        int c = argb(0xFF, accent);
        g.fill(x - 1, y - 1, x + 8, y, c);
        g.fill(x - 1, y - 1, x, y + 8, c);
        g.fill(x + w - 8, y - 1, x + w + 1, y, c);
        g.fill(x + w, y - 1, x + w + 1, y + 8, c);
        g.fill(x - 1, y + h, x + 8, y + h + 1, c);
        g.fill(x - 1, y + h - 8, x, y + h + 1, c);
        g.fill(x + w - 8, y + h, x + w + 1, y + h + 1, c);
        g.fill(x + w, y + h - 8, x + w + 1, y + h + 1, c);
    }

    public static void fillCircle(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, argb);
        }
    }

    /** Soft radial glow (layered translucent discs). */
    public static void glow(GuiGraphics g, int cx, int cy, int r, int rgb, int maxAlpha) {
        for (int k = 5; k >= 1; k--) {
            fillCircle(g, cx, cy, r * k / 5, argb(maxAlpha / 5, rgb));
        }
    }

    public static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int argb) {
        int n = Math.max(160, (int) (Math.PI * 2 * Math.max(rx, ry)));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.cos(a) * rx);
            int y = cy + (int) Math.round(Math.sin(a) * ry);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    public static void text(GuiGraphics g, Font f, Component s, int x, int y, int color, float scale, boolean centered) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1F);
        g.drawString(f, s, centered ? -f.width(s) / 2 : 0, 0, color, true);
        ps.popPose();
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int color, float scale, boolean centered) {
        text(g, f, Component.literal(s), x, y, color, scale, centered);
    }

    /** Title with a soft glow behind it. */
    public static void title(GuiGraphics g, Font f, Component s, int cx, int y, int rgb, float scale) {
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            text(g, f, s, cx + d[0], y + d[1], argb(0x55, rgb), scale, true);
        }
        text(g, f, s, cx, y, 0xFF000000 | rgb, scale, true);
    }

    public static void item(GuiGraphics g, ItemStack st, float cx, float cy, float scale) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.scale(scale, scale, 1F);
        g.renderItem(st, -8, -8);
        ps.popPose();
    }

    public static void chip(GuiGraphics g, Font f, String text, int cx, int y, int rgb) {
        int w = f.width(text) + 10;
        g.fill(cx - w / 2, y, cx + w / 2, y + 12, argb(0x66, rgb));
        g.renderOutline(cx - w / 2, y, w, 12, argb(0xFF, rgb));
        g.drawString(f, text, cx - w / 2 + 5, y + 2, 0xFFFFFFFF, true);
    }

    /** Deterministic pseudo random number in [0,1) from an index and a salt (stable between frames). */
    public static double rand(int i, int salt) {
        return (hash(i * 31 + salt * 7919) & 0xFFFFFF) / (double) 0x1000000;
    }

    /** 1px line (for streaks and sparks). */
    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
        int dx = x2 - x1, dy = y2 - y1;
        int n = Math.max(Math.abs(dx), Math.abs(dy));
        if (n == 0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, argb);
            return;
        }
        for (int i = 0; i <= n; i++) {
            int x = x1 + dx * i / n;
            int y = y1 + dy * i / n;
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    /** A light ray from (cx,cy) in direction angle, fading out along its length. */
    public static void ray(GuiGraphics g, int cx, int cy, double angle, int len, int halfWidth, int rgb, int alpha) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.mulPose(Axis.ZP.rotation((float) (angle - Math.PI / 2)));
        g.fillGradient(-halfWidth, 0, halfWidth, len, argb(alpha, rgb), argb(0, rgb));
        ps.popPose();
    }

    /** Item drawn darker for depth: brightness 1 = normal. */
    public static void itemShaded(GuiGraphics g, ItemStack st, float cx, float cy, float scale, float brightness) {
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1F);
        item(g, st, cx, cy, scale);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }
}
