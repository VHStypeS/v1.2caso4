package com.upgrader.client;

import com.upgrader.config.CrateEntry;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.util.LootRarity;
import com.upgrader.util.RarityColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Crate contents with the REAL chances: a panel of item cards, each card glowing in its rarity colour
 * (red = rare ... yellow ... green = common) with the chance printed under the item.
 */
public class PreviewScreen extends Screen {
    private static final int CW = 40;
    private static final int CH = 48;

    private final String crateId;
    private final String crateName;
    private final int rgb;
    private final List<CrateEntry> entries;
    private final List<ItemStack> icons = new ArrayList<>();
    private final boolean canOpen;
    private final double boost;
    private final int legendaryCount;
    private final int epicCount;

    private int cols = 8;
    private int rows = 4;
    private int scroll;

    public PreviewScreen(String crateId, String crateName, int rgb, List<CrateEntry> entries, boolean canOpen, double boost) {
        super(Component.literal(crateName));
        this.crateId = crateId;
        this.crateName = crateName;
        this.rgb = rgb;
        this.entries = entries;
        this.canOpen = canOpen;
        this.boost = boost;
        int leg = 0, epic = 0;
        for (CrateEntry e : entries) {
            icons.add(e.displayStack());
            if (rar(e) == LootRarity.LEGENDARY) leg++;
            else if (rar(e) == LootRarity.EPIC) epic++;
        }
        this.legendaryCount = leg;
        this.epicCount = epic;
    }

    private static LootRarity rar(CrateEntry e) {
        return e.rarity != null ? e.rarity : LootRarity.COMMON;
    }

    private int totalRows() {
        return Math.max(1, (entries.size() + cols - 1) / cols);
    }

    private int panelW() {
        return cols * CW + 24;
    }

    private int panelH() {
        return 46 + rows * CH + 8 + 36 + 28;
    }

    private int px() {
        return (width - panelW()) / 2;
    }

    private int py() {
        return Math.max(6, (height - panelH()) / 2);
    }

    @Override
    protected void init() {
        cols = Mth.clamp((width - 60) / CW, 4, 10);
        rows = Mth.clamp((height - 46 - 8 - 36 - 28 - 20) / CH, 2, 7);
        scroll = Mth.clamp(scroll, 0, Math.max(0, totalRows() - rows));
        int by = py() + panelH() - 28;
        if (canOpen) {
            FancyButton open = new FancyButton(width / 2 - 90, by, 180, 22, Component.literal("ОТКРЫТЬ"), b -> {
                Network.CHANNEL.sendToServer(new Packets.OpenRequest(crateId));
                onClose();
            }, rgb);
            open.active = !entries.isEmpty();
            addRenderableWidget(open);
        } else {
            addRenderableWidget(new FancyButton(width / 2 - 50, by, 100, 22, Component.literal("Закрыть"), b -> onClose(), rgb));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, totalRows() - rows));
        return true;
    }


    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, rgb);
        int px = px(), py = py(), pw = panelW(), ph = panelH();
        Ui.panel(g, px, py, pw, ph, rgb);

        Ui.title(g, font, Component.literal(crateName), width / 2, py + 8, rgb, 1.6F);
        g.drawCenteredString(font, entries.isEmpty() ? "Ящик пока пуст"
                : "Предметов: " + entries.size() + "  ·  легендарных: " + legendaryCount + "  ·  эпических: " + epicCount, width / 2, py + 28, 0xAAAABB);
        if (boost > 0) {
            Ui.chip(g, font, "УДАЧА: +" + RarityColor.fmt(boost) + "% к редким", px + pw - 62, py + 6, 0x55FF9A);
        }

        int gx = px + 12;
        int gy = py + 44;
        int hovered = -1;
        long t = net.minecraft.Util.getMillis();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scroll + r) * cols + c;
                if (idx >= entries.size()) break;
                CrateEntry e = entries.get(idx);
                int x = gx + c * CW;
                int y = gy + r * CH;
                boolean hov = mx >= x && mx < x + CW && my >= y && my < y + CH;
                if (hov) hovered = idx;
                LootRarity lr = rar(e);
                int col = lr.rgb;
                boolean shiny = lr.ordinal() >= LootRarity.EPIC.ordinal();
                int shimmer = shiny ? (int) (40 + 40 * Math.sin(t / 300.0 + idx)) : 0;

                g.fillGradient(x + 2, y + 2, x + CW - 2, y + CH - 2, Ui.argb(hov ? 0x66 : 0x33, col), 0xE0101020);
                g.renderOutline(x + 2, y + 2, CW - 4, CH - 4, Ui.argb((hov ? 0xFF : 0x99) + shimmer, col));
                g.fill(x + 3, y + CH - 8, x + CW - 3, y + CH - 7, Ui.argb(0xAA, col));
                if (shiny) Ui.glow(g, x + CW / 2, y + 17, lr == LootRarity.LEGENDARY ? 19 : 15, col, 40 + shimmer);
                Ui.item(g, icons.get(idx), x + CW / 2f, y + 17, hov ? 1.7F : 1.45F);
                Ui.text(g, font, RarityColor.fmt(e.chance) + "%", x + CW / 2, y + CH - 18, 0xFF000000 | RarityColor.color(e.chance), 0.8F, true);
                if (e.countMax > 1) {
                    Ui.text(g, font, "x" + e.countMax, x + CW - 5, y + 5, 0xFFAAAAAA, 0.6F, false);
                }
            }
        }
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "Пусто...", width / 2, gy + 20, 0x777788);
        }

        if (totalRows() > rows) {
            int trackX = px + pw - 8;
            int gridH = rows * CH;
            int thumbH = Math.max(12, gridH * rows / totalRows());
            int thumbY = gy + (gridH - thumbH) * scroll / Math.max(1, totalRows() - rows);
            g.fill(trackX, gy, trackX + 3, gy + gridH, 0x33FFFFFF);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Ui.argb(0xFF, rgb));
        }

        // legend: the five rarity classes
        int lx = px + 12, lw = pw - 24, ly = gy + rows * CH + 8;
        LootRarity[] rs = LootRarity.values();
        int seg = lw / rs.length;
        for (int i = 0; i < rs.length; i++) {
            g.fill(lx + i * seg, ly, lx + (i + 1) * seg - 2, ly + 4, 0xFF000000 | rs[i].rgb);
            g.drawString(font, rs[i].ru, lx + i * seg, ly + 7, 0xFF000000 | rs[i].rgb);
        }

        super.render(g, mx, my, pt);

        if (hovered >= 0) {
            CrateEntry e = entries.get(hovered);
            Minecraft mc = Minecraft.getInstance();
            List<Component> lines = new ArrayList<>(icons.get(hovered).getTooltipLines(mc.player, TooltipFlag.NORMAL));
            lines.add(Component.empty());
            lines.add(Component.literal("Шанс: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(RarityColor.fmt(e.chance) + "%")
                            .withStyle(Style.EMPTY.withColor(RarityColor.color(e.chance)).withBold(true))));
            lines.add(Component.literal("Редкость: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(rar(e).ru).withStyle(Style.EMPTY.withColor(rar(e).rgb).withBold(rar(e) == LootRarity.LEGENDARY))));
            lines.add(Component.literal("Количество: " + (e.countMin == e.countMax ? e.countMin : e.countMin + "–" + e.countMax))
                    .withStyle(ChatFormatting.GRAY));
            if (e.chance > 0) {
                lines.add(Component.literal("≈ 1 из " + Math.round(100.0 / e.chance) + " открытий").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }
}
