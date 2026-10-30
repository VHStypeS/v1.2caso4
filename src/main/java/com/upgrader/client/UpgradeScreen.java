package com.upgrader.client;

import com.upgrader.config.UpgradeConfig;
import com.upgrader.item.CrateItem;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.util.RarityColor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The Upgrader block: pick a route, pick how many crates to sacrifice, see the chance, press the button. */
public class UpgradeScreen extends Screen {
    private static final int PW = 320;
    private static final int PH = 204;

    private final Packets.OpenUpgrader data;
    private final List<Packets.OpenUpgrader.Route> routes;
    private int sel;
    private int amount;
    private int have;
    private boolean waiting;
    private long waitingSince;
    private Packets.UpgradeResult result;
    private long resultAt;
    private FancyButton go;

    public UpgradeScreen(Packets.OpenUpgrader data) {
        super(Component.literal("Upgrader"));
        this.data = data;
        this.routes = data.routes();
        this.amount = data.min();
    }

    private Packets.OpenUpgrader.Route route() {
        return routes.get(sel);
    }

    private double chance() {
        return UpgradeConfig.chance(data.points(), amount);
    }

    private int countHave() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (CrateItem.isCrate(s, route().fromId())) n += s.getCount();
        }
        return n;
    }

    private int px() {
        return (width - PW) / 2;
    }

    private int py() {
        return Math.max(4, (height - PH) / 2);
    }

    private void setAmount(int a) {
        amount = Mth.clamp(a, data.min(), data.max());
    }

    @Override
    protected void init() {
        int px = px(), py = py(), cx = width / 2;
        int rgb = route().toRgb();
        if (routes.size() > 1) {
            addRenderableWidget(new FancyButton(px + 10, py + 8, 20, 18, Component.literal("<"), b -> {
                sel = (sel + routes.size() - 1) % routes.size();
                have = countHave();
            }, rgb));
            addRenderableWidget(new FancyButton(px + PW - 30, py + 8, 20, 18, Component.literal(">"), b -> {
                sel = (sel + 1) % routes.size();
                have = countHave();
            }, rgb));
        }
        int ay = py + 128;
        addRenderableWidget(new FancyButton(cx - 84, ay, 32, 18, Component.literal("-10"), b -> setAmount(amount - 10), 0x8888AA));
        addRenderableWidget(new FancyButton(cx - 50, ay, 26, 18, Component.literal("-1"), b -> setAmount(amount - 1), 0x8888AA));
        addRenderableWidget(new FancyButton(cx + 24, ay, 26, 18, Component.literal("+1"), b -> setAmount(amount + 1), 0x8888AA));
        addRenderableWidget(new FancyButton(cx + 52, ay, 32, 18, Component.literal("+10"), b -> setAmount(amount + 10), 0x8888AA));
        int mid = data.points().size() > 1 ? data.points().get(1).amount() : (data.min() + data.max()) / 2;
        addRenderableWidget(new FancyButton(cx - 100, ay + 24, 60, 18, Component.literal("МИН " + data.min()), b -> setAmount(data.min()), rgb));
        addRenderableWidget(new FancyButton(cx - 30, ay + 24, 60, 18, Component.literal(String.valueOf(mid)), b -> setAmount(mid), rgb));
        addRenderableWidget(new FancyButton(cx + 40, ay + 24, 60, 18, Component.literal("МАКС " + data.max()), b -> setAmount(data.max()), rgb));
        go = new FancyButton(cx - 95, py + PH - 30, 190, 22, Component.literal("УЛУЧШИТЬ"), b -> {
            if (waiting || have < amount) return;
            waiting = true;
            waitingSince = Util.getMillis();
            Network.CHANNEL.sendToServer(new Packets.UpgradeRequest(route().fromId(), amount));
        }, 0x55FF77);
        addRenderableWidget(go);
        have = countHave();
    }

    @Override
    public void tick() {
        have = countHave();
        if (waiting && Util.getMillis() - waitingSince > 4000) waiting = false;
        go.active = !waiting && have >= amount && result == null;
    }

    public void onResult(Packets.UpgradeResult r) {
        waiting = false;
        result = r;
        resultAt = Util.getMillis();
        var sm = Minecraft.getInstance().getSoundManager();
        sm.play(SimpleSoundInstance.forUI(r.success() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.GLASS_BREAK, 1.0F));
        have = countHave();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (result != null && Util.getMillis() - resultAt > 500) {
            result = null;
            return true;
        }
        return result != null || super.mouseClicked(mx, my, button);
    }

    private static int chanceColor(double p) {
        return p < 50 ? Ui.mix(0xFF4444, 0xFFD83B, p / 50.0) : Ui.mix(0xFFD83B, 0x3BFF5A, (p - 50) / 50.0);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        var rt = route();
        int px = px(), py = py(), cx = width / 2;
        long t = Util.getMillis();
        double ch = chance();
        int cc = chanceColor(ch);

        Ui.backdrop(g, width, height, rt.toRgb());
        Ui.panel(g, px, py, PW, PH, rt.toRgb());
        Ui.title(g, font, Component.literal("АПГРЕЙДЕР"), cx, py + 9, rt.toRgb(), 1.5F);
        if (routes.size() > 1) {
            g.drawCenteredString(font, (sel + 1) + "/" + routes.size(), cx, py + 28, 0x777788);
        }

        // crates
        int iy = py + 78;
        int lx = px + 66, rx = px + PW - 66;
        Ui.glow(g, lx, iy, 34, rt.fromRgb(), 70);
        Ui.glow(g, rx, iy, 34 + (int) (3 * Math.sin(t / 250.0)), rt.toRgb(), 90);
        Ui.item(g, rt.fromIcon(), lx, iy, 3.2F);
        Ui.item(g, rt.toIcon(), rx, iy + (float) Math.sin(t / 300.0) * 2, 3.2F);
        Ui.text(g, font, rt.fromName(), lx, iy + 32, 0xFF000000 | rt.fromRgb(), 0.9F, true);
        Ui.text(g, font, rt.toName(), rx, iy + 32, 0xFF000000 | rt.toRgb(), 0.9F, true);
        Ui.text(g, font, "в инвентаре: " + have, lx, iy + 44, have >= amount ? 0xFF66FF88 : 0xFFFF6666, 0.8F, true);

        // moving arrows
        for (int i = 0; i < 3; i++) {
            int a = (int) (90 + 150 * Math.max(0, Math.sin(t / 220.0 - i * 0.9)));
            g.drawCenteredString(font, ">", cx - 14 + i * 14, iy + 10, Ui.argb(Math.max(a, 8), 0xFFFFFF));
        }
        Ui.text(g, font, RarityColor.fmt(Math.round(ch * 10) / 10.0) + "%", cx, iy - 20, 0xFF000000 | cc, 2.4F, true);
        Ui.text(g, font, "шанс успеха", cx, iy - 4, 0xFF9999AA, 0.8F, true);

        // chance bar with marks for the configured points
        int bx = px + 40, bw = PW - 80, by = py + 120;
        g.fill(bx, by, bx + bw, by + 4, 0x66000000);
        g.fill(bx, by, bx + (int) (bw * ch / 100.0), by + 4, 0xFF000000 | cc);
        for (var p : data.points()) {
            int mxp = bx + (int) (bw * (p.amount() - data.min()) / (double) Math.max(1, data.max() - data.min()));
            g.fill(mxp, by - 2, mxp + 1, by + 6, 0xAAFFFFFF);
        }
        int kx = bx + (int) (bw * (amount - data.min()) / (double) Math.max(1, data.max() - data.min()));
        g.fill(kx - 1, by - 3, kx + 2, by + 7, 0xFFFFFFFF);

        Ui.text(g, font, amount + " шт.", cx, py + 134, 0xFFFFFFFF, 1.3F, true);
        g.drawCenteredString(font, "При неудаче ящики сгорают", cx, py + PH - 44, 0x666677);

        super.render(g, mx, my, pt);

        // result overlay
        if (result != null) {
            long age = t - resultAt;
            float a = Mth.clamp(age / 250F, 0F, 1F);
            int col = result.success() ? 0xFFD83B : 0xFF4444;
            g.fill(0, 0, width, height, Ui.argb((int) (190 * a), 0x000000));
            Ui.glow(g, cx, height / 2 - 10, 90 + (int) (8 * Math.sin(age / 120.0)), col, (int) (110 * a));
            if (result.success()) {
                float e = Mth.clamp(age / 450F, 0F, 1F);
                Ui.item(g, result.prize(), cx, height / 2 - 10, 5.2F * (1F + 2.7F * (e - 1F) * (e - 1F) * (e - 1F) + 1.7F * (e - 1F) * (e - 1F)));
                for (int i = 0; i < 28; i++) {
                    double ang = i * Math.PI * 2 / 28 + i * 0.4;
                    double d = (1 - Math.pow(1 - Mth.clamp(age / 1400F, 0F, 1F), 3)) * (50 + (i % 4) * 25);
                    g.fill(cx + (int) (Math.cos(ang) * d), height / 2 - 10 + (int) (Math.sin(ang) * d), cx + (int) (Math.cos(ang) * d) + 2,
                            height / 2 - 8 + (int) (Math.sin(ang) * d), Ui.argb((int) (255 * (1 - Mth.clamp(age / 1400F, 0F, 1F))), col));
                }
                Ui.title(g, font, Component.literal("УСПЕХ!"), cx, height / 2 + 50, 0xFFD83B, 2.2F);
                Ui.text(g, font, "Получен: " + result.toName() + " x" + result.prize().getCount(), cx, height / 2 + 74, 0xFFFFFFFF, 1.1F, true);
            } else {
                Ui.item(g, result.prize(), cx + (float) Math.sin(age / 35.0) * 3, height / 2 - 10, 4F);
                Ui.title(g, font, Component.literal("НЕ ПОВЕЗЛО"), cx, height / 2 + 50, 0xFF4444, 2.0F);
                Ui.text(g, font, "Ящики сгорели (шанс был " + RarityColor.fmt(result.percent()) + "%)", cx, height / 2 + 74, 0xFFCCCCCC, 1.0F, true);
            }
            if (age > 500) g.drawCenteredString(font, "нажми, чтобы продолжить", cx, height - 24, 0x888899);
        }
    }
}
