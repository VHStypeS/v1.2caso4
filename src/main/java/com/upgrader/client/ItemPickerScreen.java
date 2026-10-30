package com.upgrader.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Searchable grid of EVERY registered item (all mods). Click toggles an item in the crate.
 * Search: by name or id; "@modid" filters by mod.
 */
public class ItemPickerScreen extends Screen {
    private static final int COLS = 12;
    private static final int SLOT = 20;
    private static final int GY = 52;

    private record Entry(ItemStack stack, String name, String id, String namespace) {}

    private final Screen parent;
    private final Function<ItemStack, Boolean> toggle;
    private final Predicate<ItemStack> selected;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> view = new ArrayList<>();
    private EditBox box;
    private String query = "";
    private int scroll;
    private int rowsVisible = 8;

    public ItemPickerScreen(Screen parent, Function<ItemStack, Boolean> toggle, Predicate<ItemStack> selected) {
        super(Component.literal("Выбор предметов"));
        this.parent = parent;
        this.toggle = toggle;
        this.selected = selected;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            Item item = e.getValue();
            if (item == Items.AIR) continue;
            ResourceLocation key = e.getKey().location();
            ItemStack st = new ItemStack(item);
            all.add(new Entry(st, st.getHoverName().getString().toLowerCase(Locale.ROOT), key.toString(), key.getNamespace()));
        }
        all.sort(Comparator.comparing((Entry en) -> en.namespace.equals("minecraft") ? 0 : 1)
                .thenComparing(en -> en.namespace)
                .thenComparing(en -> en.name));
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (q.isEmpty()
                    || (q.startsWith("@") ? e.namespace.startsWith(q.substring(1)) : (e.name.contains(q) || e.id.contains(q)))) {
                out.add(e);
            }
        }
        view = out;
        scroll = 0;
    }

    private int totalRows() {
        return Math.max(1, (view.size() + COLS - 1) / COLS);
    }

    private int gx() {
        return (width - COLS * SLOT) / 2;
    }

    @Override
    protected void init() {
        rowsVisible = Mth.clamp((height - GY - 50) / SLOT, 3, 14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, totalRows() - rowsVisible));
        box = new EditBox(font, width / 2 - 110, 22, 220, 18, Component.empty());
        box.setMaxLength(64);
        box.setHint(Component.literal("Поиск: название, id или @мод"));
        box.setValue(query);
        box.setResponder(s -> {
            if (!s.equals(query)) {
                query = s;
                refilter();
            }
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new FancyButton(width / 2 + 40, GY + rowsVisible * SLOT + 10, 100, 20, Component.literal("Готово"), b -> onClose(), 0x55AAFF));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 2, 0, Math.max(0, totalRows() - rowsVisible));
        return true;
    }

    private int indexAt(double mx, double my) {
        int gx = gx();
        if (mx < gx || mx >= gx + COLS * SLOT || my < GY || my >= GY + rowsVisible * SLOT) return -1;
        int c = (int) ((mx - gx) / SLOT);
        int r = (int) ((my - GY) / SLOT);
        int idx = (scroll + r) * COLS + c;
        return idx < view.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = indexAt(mx, my);
        if (idx >= 0 && button == 0) {
            toggle.apply(view.get(idx).stack.copy());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, 0x55AAFF);
        int gx = gx();
        Ui.panel(g, gx - 10, 6, COLS * SLOT + 20, GY + rowsVisible * SLOT + 40 - 6, 0x55AAFF);
        Ui.title(g, font, Component.literal("Выбор предметов"), width / 2, 10, 0x55AAFF, 1.2F);
        g.drawCenteredString(font, "Клик — добавить / убрать (зелёная рамка = уже в ящике)", width / 2, 44 - 0, 0x777788);
        int hovered = indexAt(mx, my);
        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= view.size()) break;
                Entry e = view.get(idx);
                int x = gx + c * SLOT;
                int y = GY + r * SLOT;
                boolean sel = selected.test(e.stack);
                g.fillGradient(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, sel ? 0xFF2A6A2A : idx == hovered ? 0xFF44446A : 0xFF20202E, 0xFF12121C);
                if (sel) g.renderOutline(x + 1, y + 1, SLOT - 2, SLOT - 2, 0xFF55FF55);
                g.renderItem(e.stack, x + 2, y + 2);
            }
        }
        if (view.isEmpty()) {
            g.drawCenteredString(font, "Ничего не найдено", width / 2, GY + 20, 0xFF5555);
        }
        g.drawString(font, "Найдено: " + view.size(), gx, GY + rowsVisible * SLOT + 16, 0xAAAAAA);

        super.render(g, mx, my, pt);

        if (hovered >= 0) {
            Entry e = view.get(hovered);
            List<Component> lines = new ArrayList<>(e.stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL));
            lines.add(Component.literal(e.id).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }
}
