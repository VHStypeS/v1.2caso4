package com.upgrader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Pick a whole mod: all its items go into the crate with chances computed from crafting difficulty. */
public class ModPickerScreen extends Screen {
    public interface Callback {
        /** limit 0 = every item of the mod, otherwise that many random ones. */
        void add(String namespace, int limit);
    }

    private record ModRow(String ns, String name, int count, List<ItemStack> icons) {}

    private static final int ROW_H = 30;
    private static final int PW = 400;

    private final Screen parent;
    private final Callback cb;
    private final int accent;
    private final List<ModRow> all = new ArrayList<>();
    private List<ModRow> view = new ArrayList<>();
    private EditBox box;
    private String query = "";
    private int scroll;
    private int rows = 8;

    public ModPickerScreen(Screen parent, int accent, Callback cb) {
        super(Component.literal("Выбор мода"));
        this.parent = parent;
        this.accent = accent;
        this.cb = cb;
        Map<String, List<Item>> by = new TreeMap<>();
        for (Item it : ForgeRegistries.ITEMS) {
            if (it == Items.AIR) continue;
            var id = ForgeRegistries.ITEMS.getKey(it);
            if (id == null) continue;
            by.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(it);
        }
        for (var e : by.entrySet()) {
            String name = ModList.get().getModContainerById(e.getKey()).map(c -> c.getModInfo().getDisplayName()).orElse(e.getKey());
            List<ItemStack> icons = new ArrayList<>();
            for (int i = 0; i < Math.min(4, e.getValue().size()); i++) icons.add(new ItemStack(e.getValue().get(i)));
            all.add(new ModRow(e.getKey(), name, e.getValue().size(), icons));
        }
        all.sort(Comparator.comparing((ModRow m) -> m.ns.equals("minecraft") ? 0 : 1).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        view = new ArrayList<>();
        for (ModRow m : all) {
            if (q.isEmpty() || m.ns.contains(q) || m.name.toLowerCase(Locale.ROOT).contains(q)) view.add(m);
        }
        scroll = 0;
    }

    private int px() {
        return (width - PW) / 2;
    }

    private int top() {
        return 62;
    }

    @Override
    protected void init() {
        rows = Mth.clamp((height - top() - 50) / ROW_H, 3, 12);
        scroll = Mth.clamp(scroll, 0, Math.max(0, view.size() - rows));
        box = new EditBox(font, px() + 12, 34, PW - 24, 18, Component.empty());
        box.setHint(Component.literal("Поиск мода…"));
        box.setValue(query);
        box.setResponder(s -> {
            if (!s.equals(query)) {
                query = s;
                refilter();
            }
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new FancyButton(width / 2 - 50, top() + rows * ROW_H + 10, 100, 20, Component.literal("Назад"), b -> onClose(), accent));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, view.size() - rows));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int px = px();
        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= view.size()) break;
            int y = top() + r * ROW_H;
            if (my < y || my >= y + ROW_H - 2) continue;
            ModRow m = view.get(idx);
            int bx = px + PW - 12;
            if (mx >= bx - 70 && mx < bx) {
                cb.add(m.ns, 25);
            } else if (mx >= bx - 130 && mx < bx - 74) {
                cb.add(m.ns, 0);
            } else {
                continue;
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, accent);
        int px = px();
        Ui.panel(g, px, 6, PW, top() + rows * ROW_H + 36 - 6, accent);
        Ui.title(g, font, Component.literal("Добавить предметы из мода"), width / 2, 12, accent, 1.2F);
        g.drawCenteredString(font, "Шансы считаются автоматически: чем сложнее крафт — тем реже", width / 2, 24 - 0, 0x777788);

        for (int r = 0; r < rows; r++) {
            int idx = scroll + r;
            if (idx >= view.size()) break;
            ModRow m = view.get(idx);
            int y = top() + r * ROW_H;
            boolean hov = mx >= px + 8 && mx < px + PW - 8 && my >= y && my < y + ROW_H - 2;
            g.fillGradient(px + 8, y, px + PW - 8, y + ROW_H - 2, Ui.argb(hov ? 0x44 : 0x22, accent), 0xC0101020);
            g.fill(px + 8, y, px + 10, y + ROW_H - 2, Ui.argb(0xFF, accent));
            for (int i = 0; i < m.icons.size(); i++) Ui.item(g, m.icons.get(i), px + 26 + i * 15, y + 14, 0.8F);
            g.drawString(font, font.plainSubstrByWidth(m.name, 170), px + 90, y + 4, 0xFFFFFF);
            g.drawString(font, m.ns + " · " + m.count + " предм.", px + 90, y + 16, 0x888899);
            int bx = px + PW - 12;
            boolean h1 = mx >= bx - 130 && mx < bx - 74 && hov;
            boolean h2 = mx >= bx - 70 && mx < bx && hov;
            g.fill(bx - 130, y + 6, bx - 74, y + 22, h1 ? 0xFF2E7D45 : 0xFF1F4A2C);
            g.drawCenteredString(font, "ВСЕ", bx - 102, y + 10, 0xFFFFFF);
            g.fill(bx - 70, y + 6, bx, y + 22, h2 ? 0xFF3A5AA8 : 0xFF263A6E);
            g.drawCenteredString(font, "25 случ.", bx - 35, y + 10, 0xFFFFFF);
        }
        if (view.isEmpty()) g.drawCenteredString(font, "Ничего не найдено", width / 2, top() + 20, 0xFF5555);
        super.render(g, mx, my, pt);
    }
}
