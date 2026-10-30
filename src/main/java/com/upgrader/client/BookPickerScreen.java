package com.upgrader.client;

import com.upgrader.item.CrateTier;
import com.upgrader.util.ItemPower;
import com.upgrader.util.LootRarity;
import com.upgrader.util.TierRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pick enchanted books for the crate: every enchantment of every mod, with a level selector that goes up to the
 * enchantment's real maximum (so with Apotheosis you can add Protection V, VI, VII ... and Mending / repair books).
 * A red name = the book does not fit this crate tier (you can still add it, the admin decides).
 */
public class BookPickerScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int W = 380;
    private static final int TOP = 54;

    private final Screen parent;
    private final CrateTier style;
    private final Function<ItemStack, Boolean> toggle;
    private final Predicate<ItemStack> selected;
    private final List<Enchantment> all = new ArrayList<>();
    private final Map<Enchantment, Integer> level = new HashMap<>();
    private List<Enchantment> view = new ArrayList<>();
    private String query = "";
    private int scroll;
    private int rows = 8;

    public BookPickerScreen(Screen parent, CrateTier style, Function<ItemStack, Boolean> toggle, Predicate<ItemStack> selected) {
        super(Component.literal("Книги"));
        this.parent = parent;
        this.style = style;
        this.toggle = toggle;
        this.selected = selected;
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS) all.add(e);
        all.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
        for (Enchantment e : all) level.put(e, Math.max(1, e.getMaxLevel()));
        refilter();
    }

    private static String nameOf(Enchantment e) {
        return Component.translatable(e.getDescriptionId()).getString();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Enchantment> out = new ArrayList<>();
        for (Enchantment e : all) {
            var key = ForgeRegistries.ENCHANTMENTS.getKey(e);
            if (q.isEmpty() || nameOf(e).toLowerCase(Locale.ROOT).contains(q) || (key != null && key.toString().contains(q))) out.add(e);
        }
        view = out;
        scroll = 0;
    }

    private ItemStack book(Enchantment e) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, level.get(e)));
    }

    private int left() {
        return (width - W) / 2;
    }

    @Override
    protected void init() {
        rows = Mth.clamp((height - TOP - 50) / ROW_H, 3, 12);
        scroll = Mth.clamp(scroll, 0, Math.max(0, view.size() - rows));
        int left = left();
        EditBox box = new EditBox(font, left + 12, 26, 240, 18, Component.empty());
        box.setMaxLength(48);
        box.setHint(Component.literal("Поиск: защита, починка, sharpness…"));
        box.setValue(query);
        box.setResponder(s -> {
            if (!s.equals(query)) {
                query = s;
                refilter();
                rebuildWidgets();
            }
        });
        addRenderableWidget(box);
        setInitialFocus(box);

        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= view.size()) break;
            Enchantment e = view.get(idx);
            int y = TOP + i * ROW_H;
            int max = Math.max(1, e.getMaxLevel());
            addRenderableWidget(new FancyButton(left + W - 150, y + 2, 18, 18, Component.literal("-"), b -> {
                level.put(e, Math.max(1, level.get(e) - 1));
                rebuildWidgets();
            }, 0x8888AA));
            addRenderableWidget(new FancyButton(left + W - 128, y + 2, 18, 18, Component.literal("+"), b -> {
                level.put(e, Math.min(max, level.get(e) + 1));
                rebuildWidgets();
            }, 0x8888AA));
            boolean in = selected.test(book(e));
            addRenderableWidget(new FancyButton(left + W - 106, y + 2, 94, 18, Component.literal(in ? "Убрать" : "Добавить"), b -> {
                toggle.apply(book(e));
                rebuildWidgets();
            }, in ? 0xFF5555 : 0x55FF77));
        }
        addRenderableWidget(new FancyButton(width / 2 - 50, TOP + rows * ROW_H + 8, 100, 20, Component.literal("Готово"), b -> onClose(), 0xFFD83B));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int ns = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, view.size() - rows));
        if (ns != scroll) {
            scroll = ns;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, 0xFFD83B);
        int left = left();
        Ui.panel(g, left, 6, W, TOP + rows * ROW_H + 40 - 6, 0xFFD83B);
        Ui.title(g, font, Component.literal("Книги с зачарованиями"), width / 2, 10, 0xFFD83B, 1.2F);
        g.drawString(font, "Найдено: " + view.size(), left + 258, 31, 0xAAAAAA);

        ItemStack hover = null;
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= view.size()) break;
            Enchantment e = view.get(idx);
            int y = TOP + i * ROW_H;
            ItemStack b = book(e);
            boolean fits = TierRules.bookAllowed(style, b);
            LootRarity lr = LootRarity.byRating(ItemPower.bookRating(b));
            g.fillGradient(left + 12, y, left + W - 12, y + ROW_H - 2, Ui.argb(0x22, lr.rgb), 0x90101020);
            g.fill(left + 12, y, left + 14, y + ROW_H - 2, Ui.argb(0xFF, lr.rgb));
            g.renderItem(b, left + 16, y + 3);
            String title = e.getFullname(level.get(e)).getString();
            g.drawString(font, font.plainSubstrByWidth(title, 190), left + 36, y + 3, fits ? 0xFFFFFF : 0xFF6666);
            g.drawString(font, lr.ru + (fits ? "" : " · вне правил ящика"), left + 36, y + 13, 0xFF000000 | lr.rgb);
            g.drawString(font, level.get(e) + "/" + Math.max(1, e.getMaxLevel()), left + W - 168 - font.width(level.get(e) + "/" + Math.max(1, e.getMaxLevel())) + 14, y + 7, 0xAAAAAA);
            if (mx >= left + 12 && mx < left + W - 160 && my >= y && my < y + ROW_H - 2) hover = b;
        }
        if (view.isEmpty()) g.drawCenteredString(font, "Ничего не найдено", width / 2, TOP + 20, 0xFF5555);

        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "Уровень до максимума зачарования (с Apotheosis — выше ванильного)", width / 2, TOP + rows * ROW_H + 32, 0x666677);
        if (hover != null) g.renderTooltip(font, hover, mx, my);
    }
}
