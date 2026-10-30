package com.upgrader.config;

import com.google.gson.*;
import com.upgrader.Upgrader;
import com.upgrader.item.CrateTier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.upgrader.util.LootRarity;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Server-side storage of crates and their loot.
 *  config/upgrader/custom_crates.json  - custom crate definitions
 *  config/upgrader/<crate id>.json     - loot of every crate (built-in and custom)
 *
 * Entries whose item/mod does not exist are NOT deleted from the file; they are ignored while
 * rolling and the remaining chances are renormalized proportionally.
 */
public final class CrateManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, CrateDef> DEFS = new LinkedHashMap<>();
    private static final Map<String, List<CrateEntry>> ENTRIES = new HashMap<>();
    private static final int DISPLAY_POOL_SIZE = 40;
    public static final int MAX_PREVIEW = 4096;

    private static final Set<String> RESERVED = Set.of("auth", "drops", "custom_crates", "messages");
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    /** rarity = explicit rarity of the won entry (null for old entries: the caller classifies automatically). */
    public record Roll(ItemStack stack, double percent, @Nullable LootRarity rarity) {}

    private CrateManager() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("upgrader");
    }

    // ------------------------------------------------------------------ loading

    public static synchronized void loadAll() {
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot create config dir", e);
        }
        DEFS.clear();
        ENTRIES.clear();
        for (CrateTier t : CrateTier.values()) {
            DEFS.put(t.id, new CrateDef(t.id, t.displayName, t, true));
        }
        loadCustomDefs();
        for (String id : new ArrayList<>(DEFS.keySet())) {
            loadEntries(id);
        }
    }

    private static void loadCustomDefs() {
        Path file = dir().resolve("custom_crates.json");
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("crates")) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    String name = o.has("name") ? o.get("name").getAsString() : id;
                    CrateTier style = o.has("model") ? CrateTier.byId(o.get("model").getAsString()) : CrateTier.COMMON;
                    if (style == null) style = CrateTier.COMMON;
                    if (!ID_PATTERN.matcher(id).matches() || DEFS.containsKey(id) || RESERVED.contains(id)) {
                        Upgrader.LOGGER.warn("[Upgrader] custom_crates.json: bad or duplicate id '{}' skipped", id);
                        continue;
                    }
                    DEFS.put(id, new CrateDef(id, name, style, false));
                } catch (Exception e) {
                    Upgrader.LOGGER.warn("[Upgrader] custom_crates.json: bad entry skipped: {}", el);
                }
            }
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read custom_crates.json", e);
        }
    }

    private static void saveDefs() {
        JsonArray arr = new JsonArray();
        for (CrateDef d : DEFS.values()) {
            if (d.builtin) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id);
            o.addProperty("name", d.name);
            o.addProperty("model", d.style.id);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("crates", arr);
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("custom_crates.json"), GSON.toJson(root));
        } catch (IOException e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot save custom_crates.json", e);
        }
    }

    private static void loadEntries(String id) {
        Path file = dir().resolve(id + ".json");
        List<CrateEntry> list = new ArrayList<>();
        if (!Files.exists(file)) {
            ENTRIES.put(id, list);
            saveFile(id);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonArray arr = root.has("entries") ? root.getAsJsonArray("entries") : new JsonArray();
            for (JsonElement el : arr) {
                try {
                    list.add(CrateEntry.fromJson(el.getAsJsonObject()));
                } catch (Exception e) {
                    Upgrader.LOGGER.warn("[Upgrader] {}: bad entry skipped: {} ({})", id, el, e.toString());
                }
            }
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read " + file, e);
        }
        ENTRIES.put(id, list);

        List<String> missing = list.stream().filter(e -> !e.isAvailable()).map(e -> e.id.toString()).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            Upgrader.LOGGER.warn("[Upgrader] {} crate: {} entries ignored (mod/item not found): {}. "
                    + "Remaining chances are renormalized proportionally.", id, missing.size(), missing);
        }
    }

    private static void saveFile(String id) {
        JsonObject root = new JsonObject();
        root.addProperty("crate", id);
        JsonArray arr = new JsonArray();
        for (CrateEntry e : ENTRIES.getOrDefault(id, List.of())) arr.add(e.toJson());
        root.add("entries", arr);
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(id + ".json"), GSON.toJson(root));
        } catch (IOException e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot save " + id, e);
        }
    }

    // ------------------------------------------------------------------ definitions

    @Nullable
    public static synchronized CrateDef def(String id) {
        return DEFS.get(id);
    }

    public static synchronized List<CrateDef> defs() {
        return new ArrayList<>(DEFS.values());
    }

    public static synchronized List<String> ids() {
        return new ArrayList<>(DEFS.keySet());
    }

    /** @return error text, or null on success. */
    @Nullable
    public static synchronized String create(String id, String name, CrateTier style) {
        if (!ID_PATTERN.matcher(id).matches()) return "ID: только a-z, 0-9 и _ (до 32 символов)";
        if (DEFS.containsKey(id) || RESERVED.contains(id)) return "Ящик с ID '" + id + "' уже существует";
        if (name.isBlank() || name.length() > 48) return "Название: от 1 до 48 символов";
        DEFS.put(id, new CrateDef(id, name.trim(), style, false));
        ENTRIES.put(id, new ArrayList<>());
        saveDefs();
        saveFile(id);
        return null;
    }

    @Nullable
    public static synchronized String rename(String id, String name) {
        CrateDef d = DEFS.get(id);
        if (d == null) return "Нет такого ящика";
        if (d.builtin) return "Встроенные ящики переименовать нельзя";
        if (name.isBlank() || name.length() > 48) return "Название: от 1 до 48 символов";
        DEFS.put(id, new CrateDef(d.id, name.trim(), d.style, false));
        saveDefs();
        return null;
    }

    /** The loot file stays on disk, so nothing is lost by accident. */
    @Nullable
    public static synchronized String delete(String id) {
        CrateDef d = DEFS.get(id);
        if (d == null) return "Нет такого ящика";
        if (d.builtin) return "Встроенные ящики удалить нельзя";
        DEFS.remove(id);
        ENTRIES.remove(id);
        saveDefs();
        return null;
    }

    // ------------------------------------------------------------------ loot

    public static synchronized List<CrateEntry> get(String id) {
        return new ArrayList<>(ENTRIES.getOrDefault(id, List.of()));
    }

    public static synchronized void set(String id, List<CrateEntry> list) {
        if (!DEFS.containsKey(id)) return;
        ENTRIES.put(id, new ArrayList<>(list));
        saveFile(id);
    }

    private static List<CrateEntry> active(String id) {
        return ENTRIES.getOrDefault(id, List.of()).stream()
                .filter(e -> e.chance > 0 && e.isAvailable())
                .collect(Collectors.toList());
    }

    /** Weights with the crate-luck boost: items rarer than the configured limit get (1 + boost%) times the weight. */
    private static double[] weights(List<CrateEntry> act, double boostPercent) {
        double base = 0;
        for (CrateEntry e : act) base += e.chance;
        double below = DropConfig.get().luckBelowPercent;
        double[] w = new double[act.size()];
        for (int i = 0; i < w.length; i++) {
            double pct = act.get(i).chance / base * 100.0;
            w[i] = act.get(i).chance * (boostPercent > 0 && pct < below ? 1.0 + boostPercent / 100.0 : 1.0);
        }
        return w;
    }

    @Nullable
    public static synchronized Roll roll(String id, RandomSource rnd) {
        return roll(id, rnd, 0);
    }

    /** Weighted roll. Returns the item and its real chance in percent (with luck), or null if the crate is empty. */
    @Nullable
    public static synchronized Roll roll(String id, RandomSource rnd, double boostPercent) {
        List<CrateEntry> act = active(id);
        if (act.isEmpty()) return null;
        double[] w = weights(act, boostPercent);
        double total = 0;
        for (double x : w) total += x;
        double r = rnd.nextDouble() * total;
        int pick = act.size() - 1;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) {
                pick = i;
                break;
            }
        }
        CrateEntry picked = act.get(pick);
        ItemStack s = picked.stack(Mth.nextInt(rnd, picked.countMin, picked.countMax));
        if (s.isEmpty()) return null;
        return new Roll(s, w[pick] / total * 100.0, picked.rarity);
    }

    /** Entries (icons + rarity) for the roulette animation. */
    public static synchronized List<CrateEntry> displayPool(String id) {
        List<CrateEntry> out = new ArrayList<>(active(id));
        Collections.shuffle(out);
        return out.size() > DISPLAY_POOL_SIZE ? new ArrayList<>(out.subList(0, DISPLAY_POOL_SIZE)) : out;
    }

    /** What everyone may look at: available entries with REAL chances in percent (sum = 100), most common first. */
    public static synchronized List<CrateEntry> preview(String id, double boostPercent, @Nullable Level level) {
        List<CrateEntry> act = active(id);
        double[] w = weights(act, boostPercent);
        double total = 0;
        for (double x : w) total += x;
        List<CrateEntry> out = new ArrayList<>();
        for (int i = 0; i < act.size(); i++) {
            CrateEntry e = act.get(i);
            out.add(new CrateEntry(e.id, e.tag, e.countMin, e.countMax, w[i] / total * 100.0, e.resolveRarity(level)));
        }
        out.sort((a, b) -> Double.compare(b.chance, a.chance));
        return out.size() > MAX_PREVIEW ? new ArrayList<>(out.subList(0, MAX_PREVIEW)) : out;
    }
}
