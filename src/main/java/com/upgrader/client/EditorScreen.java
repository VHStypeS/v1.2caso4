package com.upgrader.client;

import com.upgrader.config.CrateEntry;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.item.CrateTier;
import com.upgrader.util.ItemPower;
import com.upgrader.util.LootBalance;
import com.upgrader.util.LootRarity;
import com.upgrader.util.RarityColor;
import com.upgrader.util.TierRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * In-game loot editor.
 *  - "+ Предмет": searchable grid of every item of every mod.   "+ Из мода": a whole mod at once.
 *  - "Рандом": random items; the harder an item is to craft, the smaller its chance. Stackables get 1..stack size.
 *  - Every row shows the REAL chance (colour + bar). "Авто-100%": type a chance, press Enter, the rest rescales.
 */
public class EditorScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int TOP = 58;
    private static final int W = 440;
    private static final int MAX_ROWS = 4096;
    private static final int CHUNK = 120;

    private static final Pattern BAD = Pattern.compile(
            "^(air|barrier|bedrock|command_block|chain_command_block|repeating_command_block|command_block_minecart|structure_block|"
                    + "structure_void|jigsaw|debug_stick|knowledge_book|light|spawner|petrified_oak_slab|reinforced_deepslate|"
                    + "end_portal_frame|budding_amethyst|farmland|dirt_path|frogspawn|player_head|written_book|filled_map|"
                    + "enchanted_book|potion|splash_potion|lingering_potion|tipped_arrow|suspicious_sand|suspicious_gravel|"
                    + "firework_star|firework_rocket|crossbow|bundle)$|_spawn_egg$|^infested_|^test_");

    private final String crateId;
    private final String crateName;
    private final int rgb;
    private final CrateTier style;
    private final List<Row> rows = new ArrayList<>();
    private final List<RarityHit> rarityHits = new ArrayList<>();
    private final List<ChanceBox> chanceBoxes = new ArrayList<>();

    private EditBox searchBox;
    private boolean refocusSearch;
    private String search = "";
    private boolean auto = true;
    private int page;
    private int rowsPerPage = 6;
    private Component status = Component.empty();
    private FancyButton autoBtn;

    private record ChanceBox(EditBox box, Row row) {}

    private record RarityHit(int x, int y, int w, int h, Row row) {}

    private static final class Row {
        final ResourceLocation id;
        @Nullable
        final CompoundTag tag;
        final ItemStack icon;
        final boolean available;
        final String nameLower;
        final String name;
        String chance;
        String min;
        String max;
        LootRarity rarity = LootRarity.COMMON;

        Row(CrateEntry e) {
            this.id = e.id;
            this.tag = e.tag;
            this.icon = e.displayStack();
            this.available = e.isAvailable();
            this.name = available ? icon.getHoverName().getString() : e.id + " (нет мода)";
            this.nameLower = name.toLowerCase(Locale.ROOT);
            this.chance = fmt(e.chance);
            this.min = String.valueOf(e.countMin);
            this.max = String.valueOf(e.countMax);
            this.rarity = e.rarity != null ? e.rarity
                    : available ? LootRarity.auto(icon, Minecraft.getInstance().level) : LootRarity.COMMON;
        }

        Row(ItemStack st) {
            this.id = ForgeRegistries.ITEMS.getKey(st.getItem());
            this.tag = st.getTag() == null ? null : st.getTag().copy();
            this.icon = st.copy();
            this.icon.setCount(1);
            this.available = true;
            this.name = icon.getHoverName().getString();
            this.nameLower = name.toLowerCase(Locale.ROOT);
            this.chance = "1";
            this.min = String.valueOf(st.getCount());
            this.max = String.valueOf(st.getCount());
            this.rarity = LootRarity.auto(st, Minecraft.getInstance().level);
        }

        boolean same(ItemStack st) {
            return Objects.equals(id, ForgeRegistries.ITEMS.getKey(st.getItem()))
                    && Objects.equals(tag, st.getTag() == null || st.getTag().isEmpty() ? null : st.getTag());
        }
    }

    public EditorScreen(String crateId, String crateName, int rgb, CrateTier style, List<CrateEntry> entries) {
        super(Component.literal("Upgrader editor"));
        this.crateId = crateId;
        this.crateName = crateName;
        this.rgb = rgb;
        this.style = style;
        for (CrateEntry e : entries) rows.add(new Row(e));
    }

    // ------------------------------------------------------------------ helpers

    private static String fmt(double d) {
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static double parseD(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseI(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 1;
        }
    }

    private double total() {
        double t = 0;
        for (Row r : rows) if (r.available) t += parseD(r.chance);
        return t;
    }

    private double effective(Row r, double total) {
        return (total <= 0 || !r.available) ? 0 : parseD(r.chance) / total * 100.0;
    }

    private List<Row> visible() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return rows;
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (r.nameLower.contains(q) || r.id.toString().contains(q)) out.add(r);
        return out;
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + rowsPerPage - 1) / rowsPerPage);
    }

    private int left() {
        return (width - W) / 2;
    }

    private EditBox box(int x, int y, int w, String value, boolean decimal, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setMaxLength(10);
        b.setFilter(decimal ? s -> s.matches("[0-9.]*") : s -> s.matches("[0-9]*"));
        b.setValue(value);
        b.setResponder(onChange);
        return b;
    }

    // ------------------------------------------------------------------ balancing

    private void balanceFrom(Row r) {
        double v = Mth.clamp(parseD(r.chance), 0, 100);
        r.chance = fmt(v);
        double others = 0;
        int n = 0;
        for (Row o : rows) {
            if (o == r || !o.available) continue;
            others += parseD(o.chance);
            n++;
        }
        if (n == 0) {
            r.chance = "100";
            return;
        }
        double target = 100 - v;
        for (Row o : rows) {
            if (o == r || !o.available) continue;
            o.chance = fmt(others > 0 ? parseD(o.chance) / others * target : target / n);
        }
    }

    private void normalizeQuiet() {
        double t = total();
        if (t <= 0) return;
        for (Row r : rows) if (r.available) r.chance = fmt(parseD(r.chance) / t * 100.0);
    }

    private void normalize() {
        normalizeQuiet();
        rebuildWidgets();
    }

    private void equalize() {
        int n = 0;
        for (Row r : rows) if (r.available) n++;
        if (n == 0) return;
        for (Row r : rows) if (r.available) r.chance = fmt(100.0 / n);
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ adding items

    private static boolean allowed(Item it) {
        if (it == Items.AIR) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        return id != null && !id.getNamespace().equals("upgrader") && !BAD.matcher(id.getPath()).find();
    }

    @Nullable
    private static Level lvl() {
        return Minecraft.getInstance().level;
    }

    /** Rarity classes + chances of a batch by the balance rules (33% most common items, 10% legendary). share = % of the crate. */
    private void applyBalance(List<Row> batch, double share) {
        double[] rating = new double[batch.size()];
        for (int i = 0; i < rating.length; i++) rating[i] = ItemPower.rating(batch.get(i).icon, lvl());
        LootBalance.Result res = LootBalance.distribute(rating);
        for (int i = 0; i < rating.length; i++) {
            Row r = batch.get(i);
            r.rarity = res.rarity()[i];
            r.chance = fmt(Math.max(0.0001, res.percent()[i] / 100.0 * share));
        }
    }

    /** Adds a batch. It shares the crate with the old items in proportion to the item counts; old chances are scaled down. */
    private void addStacks(List<ItemStack> stacks, String what) {
        List<Row> fresh = new ArrayList<>();
        for (ItemStack st : stacks) {
            if (rows.size() + fresh.size() >= MAX_ROWS) break;
            if (findRow(st) != null) continue;
            Row r = new Row(st);
            r.min = "1";
            r.max = String.valueOf(Math.max(1, st.getMaxStackSize()));
            fresh.add(r);
        }
        if (fresh.isEmpty()) {
            status = Component.literal("Нечего добавлять (нет подходящих предметов или всё уже в ящике)").withStyle(ChatFormatting.YELLOW);
            return;
        }
        int oldCount = 0;
        for (Row r : rows) if (r.available) oldCount++;
        double oldTotal = total();
        double share = oldCount == 0 || oldTotal <= 0 ? 100 : 100.0 * fresh.size() / (fresh.size() + oldCount);
        applyBalance(fresh, share);
        if (share < 100) {
            for (Row r : rows) if (r.available) r.chance = fmt(parseD(r.chance) / oldTotal * (100 - share));
        }
        rows.addAll(fresh);
        page = pageCount() - 1;
        status = Component.literal("Добавлено: " + fresh.size() + " (" + what + "). 33% самых частых, ~10% легендарных")
                .withStyle(ChatFormatting.GREEN);
        rebuildWidgets();
    }

    /** Random items that fit this crate's tier (common: wood..diamond, mid: iron..netherite, ...). */
    private void addRandom(int n) {
        Level level = lvl();
        List<ItemStack> pool = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            if (!allowed(it)) continue;
            ItemStack st = new ItemStack(it);
            if (findRow(st) != null) continue;
            if (TierRules.allowed(style, st, level)) pool.add(st);
        }
        int fit = pool.size();
        Collections.shuffle(pool);
        addStacks(new ArrayList<>(pool.subList(0, Math.min(n, pool.size()))), "рандом " + n + ", подходит по тиру: " + fit);
    }

    /** Random enchanted books within the tier rules (common: up to II, mid: up to IV, rare+: any level, mending included). */
    private void addRandomBooks(int n) {
        Random rnd = new Random();
        List<Enchantment> ens = new ArrayList<>();
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS) if (!e.isCurse()) ens.add(e);
        List<ItemStack> books = new ArrayList<>();
        int tries = 0;
        while (books.size() < n && !ens.isEmpty() && tries++ < n * 80) {
            Enchantment e = ens.get(rnd.nextInt(ens.size()));
            int lv = 1 + rnd.nextInt(Math.max(1, e.getMaxLevel()));
            ItemStack b = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, lv));
            if (!TierRules.bookAllowed(style, b) || findRow(b) != null) continue;
            boolean dup = false;
            for (ItemStack o : books) if (ItemStack.isSameItemSameTags(o, b)) dup = true;
            if (!dup) books.add(b);
        }
        addStacks(books, "книги");
    }

    /** Whole mod at once, only items that fit the tier. */
    private void addFromMod(String ns, int limit) {
        Level level = lvl();
        List<ItemStack> pool = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            if (id == null || !id.getNamespace().equals(ns) || !allowed(it)) continue;
            ItemStack st = new ItemStack(it);
            if (TierRules.allowed(style, st, level)) pool.add(st);
        }
        if (limit > 0) {
            Collections.shuffle(pool);
            pool = new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
        }
        addStacks(pool, "мод " + ns);
    }

    /** Re-classifies ALL items of the crate by the rules: 33% most common, 10% legendary, total 100%. */
    private void rebalanceAll() {
        List<Row> av = new ArrayList<>();
        for (Row r : rows) if (r.available) av.add(r);
        if (av.isEmpty()) return;
        applyBalance(av, 100);
        status = Component.literal("Авто-баланс: редкости и шансы пересчитаны, итого 100%").withStyle(ChatFormatting.GREEN);
        rebuildWidgets();
    }

    @Nullable
    private Row findRow(ItemStack st) {
        for (Row r : rows) if (r.same(st)) return r;
        return null;
    }

    private boolean toggleItem(ItemStack st) {
        Row ex = findRow(st);
        if (ex != null) {
            rows.remove(ex);
            if (auto && !rows.isEmpty()) normalizeQuiet();
            status = Component.literal("Убрано: " + ex.name).withStyle(ChatFormatting.GRAY);
            return false;
        }
        Row r = new Row(st);
        rows.add(r);
        if (auto) balanceFrom(r);
        status = Component.literal("Добавлено: " + r.name).withStyle(ChatFormatting.GREEN);
        return true;
    }

    private void addHeld() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            status = Component.literal("Возьми предмет в основную руку (так можно добавить предмет с NBT)").withStyle(ChatFormatting.RED);
            return;
        }
        Row r = new Row(held);
        rows.add(r);
        if (auto) balanceFrom(r);
        page = pageCount() - 1;
        status = Component.literal("Добавлено: " + r.name + " (количество = " + held.getCount() + ")").withStyle(ChatFormatting.GREEN);
        rebuildWidgets();
    }

    private void save() {
        List<CrateEntry> out = new ArrayList<>();
        for (Row r : rows) out.add(new CrateEntry(r.id, r.tag, parseI(r.min), parseI(r.max), parseD(r.chance), r.rarity));
        int n = out.size();
        for (int i = 0; i == 0 || i < n; i += CHUNK) {
            int to = Math.min(n, i + CHUNK);
            Network.CHANNEL.sendToServer(new Packets.SaveEditor(crateId, i == 0, to >= n, new ArrayList<>(out.subList(i, to))));
            if (n == 0) break;
        }
        status = Component.literal("Отправлено на сервер (" + n + ")").withStyle(ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------ widgets

    private record Btn(String label, Runnable action, int accent) {}

    /** A row of buttons spread over the panel width. */
    private void buttonRow(int x, int y, int w, List<Btn> btns) {
        int gap = 4;
        int sum = 0;
        for (Btn b : btns) sum += font.width(b.label) + 14;
        int avail = w - gap * (btns.size() - 1);
        double k = (double) avail / sum;
        int cx = x;
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            int bw = i == btns.size() - 1 ? x + w - cx : (int) ((font.width(b.label) + 14) * k);
            FancyButton fb = new FancyButton(cx, y, bw, 20, Component.literal(b.label), btn -> b.action.run(), b.accent);
            addRenderableWidget(fb);
            if (b.label.startsWith("Авто")) autoBtn = fb;
            cx += bw + gap;
        }
    }

    private String autoLabel() {
        return "Авто-100%: " + (auto ? "ВКЛ" : "ВЫКЛ");
    }

    @Override
    protected void init() {
        rowsPerPage = Mth.clamp((height - TOP - 136) / ROW_H, 3, 14);
        page = Mth.clamp(page, 0, pageCount() - 1);
        chanceBoxes.clear();
        int left = left();

        if (searchBox == null) {
            searchBox = new EditBox(font, left, 28, 220, 18, Component.empty());
            searchBox.setMaxLength(48);
            searchBox.setHint(Component.literal("Поиск в ящике…"));
            searchBox.setResponder(s -> {
                if (!s.equals(search)) {
                    search = s;
                    page = 0;
                    refocusSearch = true;
                    rebuildWidgets();
                }
            });
        }
        searchBox.setX(left + 12);
        searchBox.setY(28);
        searchBox.setValue(search);
        addRenderableWidget(searchBox);
        if (refocusSearch) {
            refocusSearch = false;
            setFocused(searchBox);
            searchBox.setFocused(true);
        }
        buttonRow(left + 12 + 228, 27, W - 24 - 228, List.of(new Btn(autoLabel(), () -> {
            auto = !auto;
            autoBtn.setMessage(Component.literal(autoLabel()));
        }, 0x55FF77)));

        List<Row> vis = visible();
        for (int i = 0; i < rowsPerPage; i++) {
            int idx = page * rowsPerPage + i;
            if (idx >= vis.size()) break;
            Row r = vis.get(idx);
            int y = TOP + i * ROW_H;
            EditBox cb = box(left + 12 + 164, y + 2, 54, r.chance, true, s -> r.chance = s);
            chanceBoxes.add(new ChanceBox(cb, r));
            addRenderableWidget(cb);
            addRenderableWidget(box(left + 12 + 276, y + 2, 32, r.min, false, s -> r.min = s));
            addRenderableWidget(box(left + 12 + 310, y + 2, 32, r.max, false, s -> r.max = s));
            addRenderableWidget(new FancyButton(left + 12 + 346, y + 2, 20, 18, Component.literal("½"), b -> {
                r.chance = fmt(parseD(r.chance) / 2);
                if (auto) balanceFrom(r);
                rebuildWidgets();
            }, 0x8888AA));
            addRenderableWidget(new FancyButton(left + 12 + 368, y + 2, 20, 18, Component.literal("×2"), b -> {
                r.chance = fmt(parseD(r.chance) * 2);
                if (auto) balanceFrom(r);
                rebuildWidgets();
            }, 0x8888AA));
            addRenderableWidget(new FancyButton(left + 12 + 392, y + 2, 20, 18, Component.literal("X"), b -> {
                rows.remove(r);
                if (auto && !rows.isEmpty()) normalizeQuiet();
                rebuildWidgets();
            }, 0xFF5555));
        }

        int by = TOP + rowsPerPage * ROW_H + 6;
        int bx = left + 12, bw = W - 24;
        FancyButton prev = new FancyButton(bx, by, 20, 20, Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }, rgb);
        prev.active = page > 0;
        addRenderableWidget(prev);
        FancyButton next = new FancyButton(bx + 24, by, 20, 20, Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }, rgb);
        next.active = page < pageCount() - 1;
        addRenderableWidget(next);
        buttonRow(bx + 48, by, bw - 48, List.of(
                new Btn("+ Предмет", () -> Minecraft.getInstance().setScreen(
                        new ItemPickerScreen(this, this::toggleItem, st -> findRow(st) != null)), 0x55AAFF),
                new Btn("+ Из мода", () -> Minecraft.getInstance().setScreen(new ModPickerScreen(this, 0xFFAA33, this::addFromMod)), 0xFFAA33),
                new Btn("+ Из руки", this::addHeld, 0x55AAFF),
                new Btn("Сохранить", this::save, 0x55FF77)));
        buttonRow(bx, by + 24, bw, List.of(
                new Btn("Рандом 10", () -> addRandom(10), 0xDD66FF),
                new Btn("Рандом 30", () -> addRandom(30), 0xDD66FF),
                new Btn("Рандом 100", () -> addRandom(100), 0xDD66FF),
                new Btn("Поровну", this::equalize, 0x8888AA),
                new Btn("К 100%", this::normalize, 0x8888AA),
                new Btn("Сорт.", () -> {
                    rows.sort((a, c) -> Double.compare(parseD(c.chance), parseD(a.chance)));
                    page = 0;
                    rebuildWidgets();
                }, 0x8888AA),
                new Btn("Выход", this::onClose, 0xFF5555)));
        buttonRow(bx, by + 48, bw, List.of(
                new Btn("Книги ×5", () -> addRandomBooks(5), 0xFFD83B),
                new Btn("Книги ×15", () -> addRandomBooks(15), 0xFFD83B),
                new Btn("+ Книга (выбрать)", () -> Minecraft.getInstance().setScreen(
                        new BookPickerScreen(this, style, this::toggleItem, st -> findRow(st) != null)), 0xFFD83B),
                new Btn("Авто-баланс", this::rebalanceAll, 0x55FF77)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (RarityHit h : rarityHits) {
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.row().rarity = h.row().rarity.next();
                status = Component.literal(h.row().name + ": " + h.row().rarity.ru).withStyle(ChatFormatting.GRAY);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            for (ChanceBox cb : chanceBoxes) {
                if (cb.box.isFocused()) {
                    if (auto) {
                        balanceFrom(cb.row);
                        status = Component.literal("Остальные шансы пересчитаны, итого 100%").withStyle(ChatFormatting.GRAY);
                    }
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int np = Mth.clamp(page - (int) Math.signum(delta), 0, pageCount() - 1);
        if (np != page) {
            page = np;
            rebuildWidgets();
        }
        return true;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, rgb);
        int left = left();
        int by = TOP + rowsPerPage * ROW_H + 6;
        Ui.panel(g, left, 6, W, by + 118, rgb);
        Ui.title(g, font, Component.literal("Редактор: " + crateName), width / 2, 12, rgb, 1.3F);

        int hx = left + 12;
        g.drawString(font, "Предмет", hx + 22, TOP - 10, 0x8888AA);
        g.drawString(font, "Шанс (вес)", hx + 166, TOP - 10, 0x8888AA);
        g.drawString(font, "Реально", hx + 222, TOP - 10, 0x8888AA);
        g.drawString(font, "Мин", hx + 282, TOP - 10, 0x8888AA);
        g.drawString(font, "Макс", hx + 314, TOP - 10, 0x8888AA);

        double total = total();
        List<Row> vis = visible();
        ItemStack hoverIcon = null;
        rarityHits.clear();
        for (int i = 0; i < rowsPerPage; i++) {
            int idx = page * rowsPerPage + i;
            if (idx >= vis.size()) break;
            Row r = vis.get(idx);
            int y = TOP + i * ROW_H;
            double eff = effective(r, total);
            int col = r.available ? r.rarity.rgb : 0xFF5555;

            g.fillGradient(hx, y, left + W - 12, y + ROW_H - 1, Ui.argb(0x22, col), 0x90101020);
            g.fill(hx, y, hx + 2, y + ROW_H - 1, Ui.argb(0xFF, col));
            g.renderItem(r.icon, hx + 4, y + 3);
            g.drawString(font, font.plainSubstrByWidth(r.name, 136), hx + 24, y + 2, r.available ? 0xFFFFFF : 0xFF5555);
            String rl = r.available ? r.rarity.ru : "нет мода";
            g.drawString(font, rl, hx + 24, y + 12, 0xFF000000 | col);
            if (r.available) rarityHits.add(new RarityHit(hx + 24, y + 11, font.width(rl) + 2, 10, r));
            String effText = r.available && total > 0 ? RarityColor.fmt(eff) + "%" : "-";
            g.drawString(font, effText, hx + 224, y + 7, 0xFF000000 | RarityColor.color(eff));

            if (mx >= hx && mx < hx + 160 && my >= y && my < y + ROW_H) hoverIcon = r.icon;
        }
        if (vis.isEmpty()) {
            g.drawCenteredString(font, rows.isEmpty() ? "Ящик пуст — «+ Предмет», «+ Из мода» или «Рандом»" : "Ничего не найдено",
                    width / 2, TOP + 24, 0x888899);
        }

        super.render(g, mx, my, pt);

        boolean exact = Math.abs(total - 100) < 0.005;
        int sy = by + 76;
        g.drawString(font, Component.literal("Итого: " + RarityColor.fmt(Math.round(total * 100) / 100.0) + "%")
                .withStyle(exact ? ChatFormatting.GREEN : ChatFormatting.YELLOW), hx, sy, 0xFFFFFF);
        String right = "Предметов: " + rows.size() + " · стр. " + (page + 1) + "/" + pageCount();
        g.drawString(font, right, left + W - 12 - font.width(right), sy, 0xAAAABB);
        g.drawCenteredString(font, status, width / 2, sy + 12, 0xFFFFFF);
        g.drawCenteredString(font, "Enter в поле шанса пересчитывает остальные · клик по редкости — сменить (в чат попадают ЛЕГЕНДАРНЫЕ)",
                width / 2, sy + 24, 0x666677);
        g.drawCenteredString(font, "Правила ящика: " + TierRules.describe(style), width / 2, sy + 36, 0x8888AA);

        if (hoverIcon != null) g.renderTooltip(font, hoverIcon, mx, my);
    }
}
