package com.upgrader.config;

import com.google.gson.*;
import com.upgrader.Upgrader;
import com.upgrader.item.CrateTier;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** config/upgrader/drops.json: all drop numbers and potion settings. Chances are in percent. */
public final class DropConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Data {
        public Map<CrateTier, Double> mob = new EnumMap<>(CrateTier.class);
        public Map<CrateTier, Double> boss = new EnumMap<>(CrateTier.class);
        public Set<ResourceLocation> bossEntities = new LinkedHashSet<>();
        public Set<ResourceLocation> blacklist = new LinkedHashSet<>();
        public boolean requireRealPlayer = true;
        public boolean blockSpawnerMobs = true;
        public double minPlayerDamagePercent = 50.0;
        public double potionChance = 0.1;
        public int potionSeconds = 60;
        /** Kills during one potion after which the chances drop hard. 0 = no limit. */
        public int potionKillLimit = 0;
        public double potionLimitedChance = 0.0004;
        public Map<CrateTier, Double> mobLimited = new EnumMap<>(CrateTier.class);
        /** Brewing: how many items are used up in one brew step. */
        public int brewDiamondBlocks = 3;
        public int brewNetherite = 2;
        public int brewGunpowder = 32;
        /** Crate-luck potions: duration and how much (relative %) they raise the chance of RARE items of a crate. */
        public int luckSeconds = 120;
        public double luckBoost = 25.0;
        public double luckBoostGreater = 60.0;
        /** Items whose base chance is below this percent count as "rare" for the luck boost. */
        public double luckBelowPercent = 10.0;
        public Map<CrateTier, Double> dungeon = new EnumMap<>(CrateTier.class);
        public List<String> dungeonTables = new ArrayList<>();
    }

    private static final int CONFIG_VERSION = 3;
    private static int fileVersion = CONFIG_VERSION;
    private static volatile Data data = null;

    private DropConfig() {}

    /** Loads lazily: loot tables are loaded BEFORE the server-starting event on the first world load. */
    public static Data get() {
        Data d = data;
        if (d == null) {
            synchronized (DropConfig.class) {
                if (data == null) load();
                d = data;
            }
        }
        return d;
    }

    private static Data defaults() {
        Data d = new Data();
        d.mob.put(CrateTier.COMMON, 40.0);
        d.mob.put(CrateTier.MID, 25.0);
        d.mob.put(CrateTier.RARE, 10.0);
        d.mobLimited.put(CrateTier.COMMON, 2.5);
        d.mobLimited.put(CrateTier.MID, 0.5);
        d.mobLimited.put(CrateTier.RARE, 0.02);
        d.boss.put(CrateTier.LEGENDARY, 20.0);
        d.boss.put(CrateTier.MYTHIC, 0.4); // 1 in 250
        for (String s : List.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian")) {
            d.bossEntities.add(new ResourceLocation(s));
        }
        d.dungeon.put(CrateTier.COMMON, 8.0);
        d.dungeon.put(CrateTier.MID, 3.0);
        for (String s : List.of("simple_dungeon", "abandoned_mineshaft", "desert_pyramid", "jungle_temple",
                "stronghold_corridor", "stronghold_crossing", "stronghold_library", "woodland_mansion",
                "pillager_outpost", "ruined_portal", "shipwreck_treasure", "shipwreck_supply",
                "underwater_ruin_small", "underwater_ruin_big", "buried_treasure", "igloo_chest",
                "nether_bridge", "bastion_other", "bastion_bridge", "bastion_hoglin_stable", "bastion_treasure",
                "end_city_treasure", "ancient_city")) {
            d.dungeonTables.add("minecraft:chests/" + s);
        }
        return d;
    }

    public static synchronized void load() {
        Path file = CrateManager.dir().resolve("drops.json");
        fileVersion = CONFIG_VERSION;
        Data d = defaults();
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                fileVersion = o.has("config_version") ? o.get("config_version").getAsInt() : 0;
                if (o.has("luck")) {
                    JsonObject l = o.getAsJsonObject("luck");
                    if (l.has("duration_seconds")) d.luckSeconds = Math.max(1, l.get("duration_seconds").getAsInt());
                    if (l.has("boost_percent")) d.luckBoost = l.get("boost_percent").getAsDouble();
                    if (l.has("greater_boost_percent")) d.luckBoostGreater = l.get("greater_boost_percent").getAsDouble();
                    if (l.has("affects_items_below_percent")) d.luckBelowPercent = l.get("affects_items_below_percent").getAsDouble();
                }
                if (o.has("mob_drop_chance_percent")) d.mob = readChances(o.getAsJsonObject("mob_drop_chance_percent"));
                if (o.has("boss_drop_chance_percent")) d.boss = readChances(o.getAsJsonObject("boss_drop_chance_percent"));
                if (o.has("boss_entities")) d.bossEntities = readIds(o.getAsJsonArray("boss_entities"));
                if (o.has("mob_blacklist")) d.blacklist = readIds(o.getAsJsonArray("mob_blacklist"));
                if (o.has("require_real_player")) d.requireRealPlayer = o.get("require_real_player").getAsBoolean();
                if (o.has("block_spawner_mobs")) d.blockSpawnerMobs = o.get("block_spawner_mobs").getAsBoolean();
                if (o.has("min_player_damage_percent")) d.minPlayerDamagePercent = o.get("min_player_damage_percent").getAsDouble();
                if (o.has("podkrytka")) {
                    JsonObject p = o.getAsJsonObject("podkrytka");
                    if (p.has("legendary_chance_percent")) d.potionChance = p.get("legendary_chance_percent").getAsDouble();
                    if (p.has("duration_seconds")) d.potionSeconds = p.get("duration_seconds").getAsInt();
                    if (p.has("kill_limit")) d.potionKillLimit = p.get("kill_limit").getAsInt();
                    if (p.has("limited_legendary_chance_percent")) d.potionLimitedChance = p.get("limited_legendary_chance_percent").getAsDouble();
                    if (p.has("limited_mob_drop_chance_percent")) d.mobLimited = readChances(p.getAsJsonObject("limited_mob_drop_chance_percent"));
                    if (p.has("brew_diamond_blocks")) d.brewDiamondBlocks = Math.max(1, p.get("brew_diamond_blocks").getAsInt());
                    if (p.has("brew_netherite_ingots")) d.brewNetherite = Math.max(1, p.get("brew_netherite_ingots").getAsInt());
                    if (p.has("brew_gunpowder")) d.brewGunpowder = Math.max(1, p.get("brew_gunpowder").getAsInt());
                }
                if (o.has("dungeon_chest_chance_percent")) d.dungeon = readChances(o.getAsJsonObject("dungeon_chest_chance_percent"));
                if (o.has("dungeon_loot_tables")) {
                    d.dungeonTables = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray("dungeon_loot_tables")) d.dungeonTables.add(el.getAsString());
                }
            }
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read drops.json, using defaults", e);
            d = defaults();
        }
        // 1.3: new default mob chances (40/25/10) and no kill limit. Old files are migrated once.
        int version = fileVersion;
        if (version < CONFIG_VERSION) {
            Data def = defaults();
            d.mob = def.mob;
            d.potionKillLimit = 0;
        }
        // Dungeon chests give only the first two tiers.
        d.dungeon.keySet().removeIf(t -> t != CrateTier.COMMON && t != CrateTier.MID);
        data = d;
        write(file, d); // also adds any new keys to an old file
    }

    private static Map<CrateTier, Double> readChances(JsonObject o) {
        Map<CrateTier, Double> m = new EnumMap<>(CrateTier.class);
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            CrateTier t = CrateTier.byId(e.getKey());
            if (t == null) {
                Upgrader.LOGGER.warn("[Upgrader] drops.json: unknown crate '{}'", e.getKey());
                continue;
            }
            m.put(t, e.getValue().getAsDouble());
        }
        return m;
    }

    private static Set<ResourceLocation> readIds(JsonArray arr) {
        Set<ResourceLocation> s = new LinkedHashSet<>();
        for (JsonElement el : arr) {
            ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
            if (id != null) s.add(id);
        }
        return s;
    }

    private static void write(Path file, Data d) {
        JsonObject root = new JsonObject();
        root.addProperty("config_version", CONFIG_VERSION);
        JsonObject luck = new JsonObject();
        luck.addProperty("duration_seconds", d.luckSeconds);
        luck.addProperty("boost_percent", d.luckBoost);
        luck.addProperty("greater_boost_percent", d.luckBoostGreater);
        luck.addProperty("affects_items_below_percent", d.luckBelowPercent);
        root.add("luck", luck);
        root.add("mob_drop_chance_percent", chancesJson(d.mob));
        root.add("boss_drop_chance_percent", chancesJson(d.boss));
        JsonArray bosses = new JsonArray();
        d.bossEntities.forEach(id -> bosses.add(id.toString()));
        root.add("boss_entities", bosses);
        JsonArray black = new JsonArray();
        d.blacklist.forEach(id -> black.add(id.toString()));
        root.add("mob_blacklist", black);
        root.addProperty("require_real_player", d.requireRealPlayer);
        root.addProperty("block_spawner_mobs", d.blockSpawnerMobs);
        root.addProperty("min_player_damage_percent", d.minPlayerDamagePercent);
        JsonObject pod = new JsonObject();
        pod.addProperty("legendary_chance_percent", d.potionChance);
        pod.addProperty("duration_seconds", d.potionSeconds);
        pod.addProperty("kill_limit", d.potionKillLimit);
        pod.addProperty("limited_legendary_chance_percent", d.potionLimitedChance);
        pod.add("limited_mob_drop_chance_percent", chancesJson(d.mobLimited));
        pod.addProperty("brew_diamond_blocks", d.brewDiamondBlocks);
        pod.addProperty("brew_netherite_ingots", d.brewNetherite);
        pod.addProperty("brew_gunpowder", d.brewGunpowder);
        root.add("podkrytka", pod);
        root.add("dungeon_chest_chance_percent", chancesJson(d.dungeon));
        JsonArray tables = new JsonArray();
        d.dungeonTables.forEach(tables::add);
        root.add("dungeon_loot_tables", tables);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot write drops.json", e);
        }
    }

    private static JsonObject chancesJson(Map<CrateTier, Double> m) {
        JsonObject o = new JsonObject();
        for (CrateTier t : CrateTier.values()) {
            if (m.containsKey(t)) o.addProperty(t.id, m.get(t));
        }
        return o;
    }
}
