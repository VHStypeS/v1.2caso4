package com.upgrader.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.upgrader.item.CrateItem;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.util.LootRarity;
import com.upgrader.util.RarityColor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Solar system" roulette: two counter-rotating rings of items circle a pulsing core with runes, warp streaks fly
 * past while it spins, every item has an aura in its OWN rarity colour, the ring slows down and the prize (chosen
 * by the server) stops in front. Then: flash, shockwaves, rotating light rays, sparks, confetti (epic/legendary),
 * screen shake (legendary) and a result card with the rarity. ESC while it runs = skip.
 */
public class CrateScreen extends Screen {
    private static final int K = 14;   // outer ring
    private static final int KI = 8;   // inner ring
    private static final long T_SPIN = 6200;
    private static final long T_REVEAL = 1300;

    private final String crateId;
    private final String crateName;
    private final int rgb;
    private final ItemStack winner;
    private final LootRarity rarity;
    private final int rcol;
    private final double percent;

    private final List<ItemStack> outer = new ArrayList<>();      // index 0 = the winner
    private final LootRarity[] outerR = new LootRarity[K];
    private final List<ItemStack> inner = new ArrayList<>();
    private final LootRarity[] innerR = new LootRarity[KI];
    private final double startAngle;
    private final double innerPhase;
    private final double totalAngle;

    private long start = Util.getMillis();
    private int lastSeg = -1;
    private boolean revealed;
    private boolean doneSent;
    private boolean buttonsAdded;
    private FancyButton again;

    private record Draw(double depth, int kind, int idx) {}

    public CrateScreen(String crateId, String crateName, int rgb, List<ItemStack> pool, List<LootRarity> poolRarity,
                       ItemStack winner, LootRarity rarity, double percent) {
        super(Component.empty());
        this.crateId = crateId;
        this.crateName = crateName;
        this.rgb = rgb;
        this.winner = winner;
        this.rarity = rarity;
        this.rcol = rarity.rgb;
        this.percent = percent;

        RandomSource r = RandomSource.create();
        ItemStack w = winner.copy();
        w.setCount(1);
        outer.add(w);
        outerR[0] = rarity;
        for (int i = 1; i < K; i++) pick(r, pool, poolRarity, outer, outerR, i, winner, rarity);
        for (int i = 0; i < KI; i++) pick(r, pool, poolRarity, inner, innerR, i, winner, rarity);

        startAngle = r.nextDouble() * Math.PI * 2;
        innerPhase = r.nextDouble() * Math.PI * 2;
        double toFront = ((Math.PI / 2 - startAngle) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        totalAngle = Math.PI * 2 * 4 + toFront; // 4+ full turns, ends with the winner in front
    }

    private static void pick(RandomSource r, List<ItemStack> pool, List<LootRarity> pr, List<ItemStack> out,
                             LootRarity[] outR, int i, ItemStack winner, LootRarity winR) {
        ItemStack s;
        LootRarity lr;
        if (pool.isEmpty()) {
            s = winner;
            lr = winR;
        } else {
            int k = r.nextInt(pool.size());
            s = pool.get(k);
            lr = k < pr.size() ? pr.get(k) : LootRarity.COMMON;
        }
        ItemStack c = s.copy();
        c.setCount(1);
        out.add(c);
        outR[i] = lr;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isDone() {
        return Util.getMillis() - start > T_SPIN + T_REVEAL + 250;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !isDone()) {
            start = Util.getMillis() - (T_SPIN + T_REVEAL + 300); // jump to the finished state
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (!doneSent) {
            doneSent = true;
            Network.CHANNEL.sendToServer(new Packets.OpenDone());
        }
        super.removed();
    }

    private int buttonsY() {
        return height / 2 - 18 + 116;
    }

    @Override
    public void tick() {
        if (!isDone()) return;
        if (!doneSent) {
            doneSent = true;
            Network.CHANNEL.sendToServer(new Packets.OpenDone());
        }
        int by = buttonsY();
        if (!buttonsAdded) {
            buttonsAdded = true;
            again = new FancyButton(width / 2 - 106, by, 102, 20, Component.literal("Открыть ещё"), b -> {
                Network.CHANNEL.sendToServer(new Packets.OpenRequest(crateId));
                again.active = false;
            }, rgb);
            addRenderableWidget(again);
            addRenderableWidget(new FancyButton(width / 2 + 4, by, 102, 20, Component.literal("Закрыть"), b -> onClose(), 0x8888AA));
        }
        var p = Minecraft.getInstance().player;
        again.active = p != null && (CrateItem.isCrate(p.getMainHandItem(), crateId) || CrateItem.isCrate(p.getOffhandItem(), crateId));
    }

    private static float easeOutBack(float x) {
        float p = x - 1F;
        return 1F + 2.70158F * p * p * p + 1.70158F * p * p;
    }

    private static void play(SoundEvent ev, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    private void revealSound() {
        switch (rarity) {
            case LEGENDARY -> {
                play(SoundEvents.TOTEM_USE, 0.8F, 1.0F);
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
            case EPIC -> play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
            case RARE -> play(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
            default -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 0.9F);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis() - start;
        int cx = width / 2;
        int cy = height / 2 - 18;

        float spin = Mth.clamp(now / (float) T_SPIN, 0F, 1F);
        float p = Mth.clamp((now - T_SPIN) / (float) T_REVEAL, 0F, 1F);
        long since = Math.max(0, now - T_SPIN);          // ms since the prize was revealed
        double eased = 1.0 - Math.pow(1.0 - spin, 3.4);
        double vel = spin >= 1F ? 0 : Math.pow(1.0 - spin, 2.4); // how fast it spins right now, 1 -> 0
        double ang = totalAngle * eased;

        int seg = (int) Math.floor(ang / (Math.PI * 2 / K));
        if (spin < 1F && seg != lastSeg) {
            lastSeg = seg;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.55F, 0.7F + 0.9F * (1F - (float) Math.pow(1F - spin, 2)));
        }
        if (p > 0F && !revealed) {
            revealed = true;
            revealSound();
        }

        Ui.backdrop(g, width, height, rgb);

        // flash at the moment of the reveal
        if (since > 0 && since < 420) {
            float amount = rarity == LootRarity.LEGENDARY ? 0.55F : rarity == LootRarity.EPIC ? 0.35F : 0.18F;
            g.fill(0, 0, width, height, Ui.argb((int) (255 * amount * (1F - since / 420F)), 0xFFFFFF));
        }

        PoseStack ps = g.pose();
        ps.pushPose();
        // screen shake for epic / legendary
        if (since > 0 && since < 800 && rarity.ordinal() >= LootRarity.EPIC.ordinal()) {
            float amp = (rarity == LootRarity.LEGENDARY ? 4F : 2F) * (1F - since / 800F);
            ps.translate((Math.random() - 0.5) * amp * 2, (Math.random() - 0.5) * amp * 2, 0);
        }

        int rx = Math.min(width * 3 / 10, 150);
        int ry = (int) (rx * 0.40);
        float orbitFade = 1F - Mth.clamp(p * 1.4F, 0F, 1F);

        // big nebula behind everything
        int pulse = (int) (40 + 14 * Math.sin(now / 260.0));
        int baseCol = p > 0 ? rcol : rgb;
        Ui.glow(g, cx, cy, 78 + (int) (p * 70), baseCol, p > 0 ? (int) (120 * (1 - Math.max(0, p - 0.7) * 2)) + 24 : pulse);
        Ui.glow(g, cx, cy + 10, 130, Ui.mix(rgb, 0x6633FF, 0.5), 22);

        // warp streaks: long while it spins fast, gone when it stops
        if (vel > 0.03) {
            for (int i = 0; i < 30; i++) {
                double a = i * 2.399963 + Ui.rand(i, 1) * 0.6;
                double speed = (0.07 + 0.30 * vel) * (0.6 + (i % 5) * 0.18);
                double rr = 24 + ((now * speed + i * 53) % 230);
                double len = 4 + 34 * vel * (0.5 + Ui.rand(i, 2));
                int x1 = cx + (int) (Math.cos(a) * rr);
                int y1 = cy + (int) (Math.sin(a) * rr * 0.72);
                int x2 = cx + (int) (Math.cos(a) * (rr + len));
                int y2 = cy + (int) (Math.sin(a) * (rr + len) * 0.72);
                int al = (int) (150 * vel * Math.min(1.0, rr / 80.0));
                Ui.line(g, x1, y1, x2, y2, Ui.argb(al, i % 3 == 0 ? 0xFFFFFF : rgb));
            }
        }

        // orbit guide rings
        if (orbitFade > 0.02F) {
            Ui.ellipse(g, cx, cy, rx, ry, Ui.argb((int) (80 * orbitFade), rgb));
            Ui.ellipse(g, cx, cy, rx * 52 / 100, ry * 52 / 100, Ui.argb((int) (55 * orbitFade), rgb));
        }

        // selector frame in front of the ring (where the prize will stop)
        if (p < 1F) {
            int fy = cy + ry;
            int fa = (int) ((110 + 60 * Math.sin(now / 160.0)) * orbitFade);
            if (fa > 4) {
                g.fillGradient(cx - 26, fy - 26, cx + 26, fy + 26, Ui.argb(fa / 4, rgb), Ui.argb(0, rgb));
                g.renderOutline(cx - 26, fy - 26, 52, 52, Ui.argb(fa, rgb));
                // arrow under the frame
                for (int i = 0; i < 6; i++) g.fill(cx - 6 + i, fy + 30 + i, cx + 7 - i, fy + 31 + i, Ui.argb(Math.min(255, fa + 60), 0xFFFFFF));
            }
        }

        // things on the ring sorted by depth: far -> near (the core is "depth 0.5")
        double[] th = new double[K];
        double[] th2 = new double[KI];
        List<Draw> draws = new ArrayList<>();
        for (int i = 0; i < K; i++) {
            th[i] = startAngle + i * Math.PI * 2 / K + ang;
            draws.add(new Draw((Math.sin(th[i]) + 1) / 2, 0, i));
        }
        if (orbitFade > 0.02F) {
            for (int i = 0; i < KI; i++) {
                th2[i] = innerPhase + i * Math.PI * 2 / KI - ang * 0.75;
                draws.add(new Draw((Math.sin(th2[i]) + 1) / 2, 1, i));
            }
        }
        draws.add(new Draw(0.5, 2, 0));
        draws.sort((a, b) -> Double.compare(a.depth(), b.depth()));

        for (Draw d : draws) {
            if (d.kind() == 2) {
                drawCore(g, cx, cy, now, p, orbitFade);
            } else if (d.kind() == 0) {
                int i = d.idx();
                if (i == 0 && p > 0F) continue; // the winner is drawn separately
                double depth = d.depth();
                float sc = (float) (1.0 + 1.5 * depth) * (p > 0F ? orbitFade : 1F);
                if (sc < 0.08F) continue;
                float spread = 1F + p * 0.5F;
                float x = (float) (cx + Math.cos(th[i]) * rx * spread);
                float y = (float) (cy + Math.sin(th[i]) * ry * spread);
                int col = outerR[i].rgb;
                Ui.glow(g, (int) x, (int) y, (int) (9 * sc) + 4, col, (int) (30 + 70 * depth));
                Ui.itemShaded(g, outer.get(i), x, y, sc, (float) (0.5 + 0.5 * depth));
            } else {
                int i = d.idx();
                double depth = d.depth();
                float sc = (float) (0.75 + 0.7 * depth) * orbitFade;
                if (sc < 0.08F) continue;
                float x = (float) (cx + Math.cos(th2[i]) * rx * 0.52);
                float y = (float) (cy + Math.sin(th2[i]) * ry * 0.52);
                Ui.glow(g, (int) x, (int) y, (int) (8 * sc) + 3, innerR[i].rgb, (int) (22 + 50 * depth));
                Ui.itemShaded(g, inner.get(i), x, y, sc, (float) (0.4 + 0.5 * depth));
            }
        }

        if (p <= 0F) {
            // winner still travelling on the ring: draw it again on top so that it is never hidden by the core
            double depth = (Math.sin(th[0]) + 1) / 2;
            if (depth > 0.5) {
                Ui.item(g, outer.get(0), (float) (cx + Math.cos(th[0]) * rx), (float) (cy + Math.sin(th[0]) * ry), (float) (1.0 + 1.5 * depth));
            }
        }

        if (p > 0F) drawReveal(g, cx, cy, now, since, p);
        ps.popPose();

        if (!isDone()) {
            g.drawCenteredString(font, "ESC — пропустить анимацию", cx, height - 22, 0x777788);
        }
        if (p >= 1F) drawResult(g, cx, cy, now);
        super.render(g, mx, my, pt);
    }

    /** Pulsing core with two counter-rotating rune rings. */
    private void drawCore(GuiGraphics g, int cx, int cy, long now, float p, float orbitFade) {
        float fade = Math.max(orbitFade, 0.0F);
        if (fade <= 0.02F) return;
        int core = p > 0 ? rcol : rgb;
        double beat = 1.0 + 0.12 * Math.sin(now / 210.0);
        Ui.glow(g, cx, cy, (int) (26 * beat), core, (int) (150 * fade));
        Ui.fillCircle(g, cx, cy, (int) (7 * beat), Ui.argb((int) (230 * fade), 0xFFFFFF));
        Ui.fillCircle(g, cx, cy, (int) (4 * beat), Ui.argb((int) (255 * fade), core));
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36 + now / 1400.0;
            int x = cx + (int) Math.round(Math.cos(a) * 30);
            int y = cy + (int) Math.round(Math.sin(a) * 30 * 0.42);
            int s = i % 3 == 0 ? 2 : 1;
            g.fill(x, y, x + s, y + s, Ui.argb((int) ((i % 3 == 0 ? 230 : 110) * fade), i % 3 == 0 ? 0xFFFFFF : core));
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24 - now / 900.0;
            int x = cx + (int) Math.round(Math.cos(a) * 44);
            int y = cy + (int) Math.round(Math.sin(a) * 44 * 0.42);
            g.fill(x, y, x + 1, y + 1, Ui.argb((int) (140 * fade), core));
        }
    }

    /** Shockwaves, light rays, the prize flying to the centre, sparks, confetti. */
    private void drawReveal(GuiGraphics g, int cx, int cy, long now, long since, float p) {
        int rcount = rarity.ordinal() >= LootRarity.RARE.ordinal() ? 16 : 8;
        int rayAlpha = switch (rarity) {
            case LEGENDARY -> 120;
            case EPIC -> 90;
            case RARE -> 66;
            default -> 40;
        };
        float rayGrow = Mth.clamp(since / 700F, 0F, 1F);
        for (int i = 0; i < rcount; i++) {
            double a = i * Math.PI * 2 / rcount + now / 2600.0 * (i % 2 == 0 ? 1 : -0.6);
            Ui.ray(g, cx, cy, a, (int) ((70 + 80 * (i % 3) / 2.0) * rayGrow * 1.6), 3 + (i % 2) * 2, rcol, rayAlpha);
        }

        // shockwaves
        for (int k = 0; k < 3; k++) {
            float t = Mth.clamp((since - k * 170L) / 950F, 0F, 1F);
            if (t <= 0F || t >= 1F) continue;
            int r = (int) (t * 200);
            Ui.ellipse(g, cx, cy, r, (int) (r * 0.62), Ui.argb((int) (190 * (1F - t)), k == 0 ? 0xFFFFFF : rcol));
        }

        // the prize flies to the centre and grows
        float e = easeOutBack(p);
        float y = cy + 40 * (1F - Mth.clamp(p * 1.3F, 0F, 1F));
        float bob = p >= 1F ? (float) Math.sin(now / 420.0) * 3F : 0F;
        float scale = 2.5F + 2.7F * e + (p >= 1F ? (float) Math.sin(now / 500.0) * 0.12F : 0F);
        Ui.glow(g, cx, (int) (y + bob), (int) (22 * scale / 2.5F), rcol, 70);
        Ui.item(g, outer.get(0), cx, y + bob, scale);

        // sparks
        float st = Mth.clamp(since / 1500F, 0F, 1F);
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36 + i * 0.37;
            double d = (1 - Math.pow(1 - st, 3)) * (60 + (i % 5) * 24);
            int sx = cx + (int) (Math.cos(a) * d);
            int sy = cy + (int) (Math.sin(a) * d * 0.85);
            g.fill(sx, sy, sx + 2, sy + 2, Ui.argb((int) (255 * (1 - st)), i % 2 == 0 ? rcol : 0xFFFFFF));
        }

        // confetti: epic and legendary
        int confetti = rarity == LootRarity.LEGENDARY ? 80 : rarity == LootRarity.EPIC ? 40 : 0;
        for (int i = 0; i < confetti; i++) {
            double fx = Ui.rand(i, 3);
            double speed = 0.07 + Ui.rand(i, 4) * 0.12;
            double fy = ((since * speed + Ui.rand(i, 5) * (height + 40)) % (height + 40)) - 20;
            int x = (int) (fx * width + Math.sin(now / 300.0 + i) * 8);
            int c = switch (i % 3) {
                case 0 -> rcol;
                case 1 -> 0xFFFFFF;
                default -> Ui.mix(rcol, 0xFFFFFF, 0.5);
            };
            int w = 2 + (i % 3);
            g.fill(x, (int) fy, x + w, (int) fy + w + 1, Ui.argb(210, c));
        }
    }

    private void drawResult(GuiGraphics g, int cx, int cy, long now) {
        float a = Mth.clamp((now - T_SPIN - T_REVEAL) / 250F, 0F, 1F);
        int top = cy + 46;
        int pw = 230;
        int al = (int) (255 * a);
        g.fillGradient(cx - pw / 2, top, cx + pw / 2, top + 64, Ui.argb((int) (0xDD * a), 0x14142A), Ui.argb((int) (0xDD * a), 0x08080F));
        g.renderOutline(cx - pw / 2, top, pw, 64, Ui.argb(al, rcol));
        g.fill(cx - pw / 2, top, cx + pw / 2, top + 2, Ui.argb(al, rcol));

        Ui.text(g, font, winner.getHoverName(), cx, top + 6, Ui.argb(al, 0xFFFFFF), 1.25F, true);
        String label = (rarity == LootRarity.LEGENDARY || rarity == LootRarity.EPIC ? "★ " : "")
                + rarity.ru.toUpperCase()
                + (rarity == LootRarity.LEGENDARY || rarity == LootRarity.EPIC ? " ★" : "");
        int chipAlpha = rarity == LootRarity.LEGENDARY ? (int) (200 + 55 * Math.sin(now / 150.0)) : 255;
        if (a > 0.3F) {
            Ui.chip(g, font, label, cx, top + 22, rcol);
        }
        g.drawCenteredString(font, "x" + winner.getCount(), cx, top + 38, Ui.argb(Math.max(8, Math.min(al, chipAlpha)), rgb));
        g.drawCenteredString(font, "шанс " + RarityColor.fmt(percent) + "%", cx, top + 51, Ui.argb(Math.max(8, al / 2), 0xAAAAAA));
    }
}
