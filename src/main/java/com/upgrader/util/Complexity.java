package com.upgrader.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

/**
 * "How hard is this item to get?" computed from the recipes the client knows (vanilla + every mod).
 * cost(raw item)   = base value (1 for dirt/logs, much more for diamonds, netherite scrap, nether stars ...)
 * cost(crafted)    = cheapest recipe: sum of ingredient costs / output count + a small step cost.
 * More stages and more expensive materials => bigger cost => smaller chance (weight = 1 / cost^1.5).
 */
public final class Complexity {
    private static final double STEP = 0.6;
    private static final double INF = 1e18;

    private final Map<Item, Double> cost = new HashMap<>();

    private record Alt(Item out, int count, List<Item[]> ings) {}

    private static Complexity cached;
    private static Object cachedKey;

    /** Works on the client AND on the server: both know the recipes (vanilla + every mod). Cached per recipe manager. */
    public static synchronized Complexity of(@Nullable Level lvl) {
        Object key = lvl == null ? "none" : lvl.getRecipeManager();
        if (cached == null || cachedKey != key) {
            cached = new Complexity(lvl);
            cachedKey = key;
        }
        return cached;
    }

    /** Hand-made values for things that cannot be crafted but are obviously rare. */
    private static double special(String path) {
        return switch (path) {
            case "diamond" -> 24;
            case "emerald" -> 14;
            case "netherite_scrap", "ancient_debris" -> 40;
            case "nether_star" -> 90;
            case "dragon_egg" -> 250;
            case "elytra" -> 120;
            case "totem_of_undying" -> 70;
            case "trident" -> 50;
            case "heart_of_the_sea" -> 60;
            case "enchanted_golden_apple" -> 150;
            case "shulker_shell" -> 25;
            case "echo_shard" -> 30;
            case "dragon_head" -> 80;
            case "wither_skeleton_skull" -> 45;
            case "music_disc_pigstep", "music_disc_otherside" -> 60;
            case "netherite_upgrade_smithing_template" -> 60;
            case "sponge", "wet_sponge" -> 12;
            case "gold_ingot", "raw_gold", "gold_ore", "deepslate_gold_ore", "nether_gold_ore" -> 6;
            case "iron_ore", "raw_iron", "deepslate_iron_ore" -> 3;
            case "lapis_lazuli", "redstone", "copper_ingot", "raw_copper" -> 3;
            case "quartz", "amethyst_shard", "glowstone_dust", "blaze_rod", "ender_pearl", "ghast_tear" -> 5;
            case "coal", "charcoal", "string", "bone", "gunpowder", "slime_ball", "leather" -> 2;
            default -> -1;
        };
    }

    private static double base(Item it) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
        String path = id == null ? "" : id.getPath();
        double sp = special(path);
        if (sp > 0) return sp;
        double r = 1;
        Rarity rar = it.getRarity(new ItemStack(it));
        if (rar == Rarity.UNCOMMON) r = 2;
        else if (rar == Rarity.RARE) r = 6;
        else if (rar == Rarity.EPIC) r = 20;
        if (path.startsWith("raw_") || path.endsWith("_ore") || path.endsWith("_scrap") || path.endsWith("_shard")) r = Math.max(r, 3);
        return r;
    }

    private Complexity(@Nullable Level level) {
        List<Alt> alts = new ArrayList<>();
        Set<Item> produced = new HashSet<>();
        if (level != null) {
            RegistryAccess ra = level.registryAccess();
            for (Recipe<?> r : level.getRecipeManager().getRecipes()) {
                try {
                    ItemStack res = r.getResultItem(ra);
                    if (res.isEmpty()) continue;
                    List<Item[]> ings = new ArrayList<>();
                    for (Ingredient ing : r.getIngredients()) {
                        ItemStack[] st = ing.getItems();
                        if (st.length == 0) continue;
                        int n = Math.min(st.length, 6);
                        Item[] arr = new Item[n];
                        for (int i = 0; i < n; i++) arr[i] = st[i].getItem();
                        ings.add(arr);
                    }
                    if (ings.isEmpty()) continue;
                    Item out = res.getItem();
                    alts.add(new Alt(out, Math.max(1, res.getCount()), ings));
                    // smelting & co. are one more stage
                    if (r instanceof AbstractCookingRecipe) ings.add(new Item[]{Items.COAL});
                    produced.add(out);
                } catch (Throwable ignored) {
                }
            }
        }
        // raw = nobody crafts it, or it has a hand-made value (diamond is craftable from a block, but it is raw)
        for (Item it : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            boolean raw = !produced.contains(it) || (id != null && special(id.getPath()) > 0);
            cost.put(it, raw ? base(it) : INF);
        }
        Set<Item> fixed = new HashSet<>();
        for (Map.Entry<Item, Double> e : cost.entrySet()) if (e.getValue() < INF) fixed.add(e.getKey());

        for (int round = 0; round < 10; round++) {
            boolean changed = false;
            for (Alt a : alts) {
                if (fixed.contains(a.out)) continue;
                double sum = 0;
                for (Item[] opts : a.ings) {
                    double best = INF;
                    for (Item i : opts) best = Math.min(best, cost.getOrDefault(i, INF));
                    sum += best;
                    if (sum >= INF) break;
                }
                if (sum >= INF) continue;
                double c = sum / a.count + STEP;
                if (c < cost.getOrDefault(a.out, INF) - 1e-9) {
                    cost.put(a.out, c);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        for (Map.Entry<Item, Double> e : cost.entrySet()) {
            if (e.getValue() >= INF) e.setValue(Math.max(8, base(e.getKey())));
        }
    }

    public double cost(Item it) {
        return Math.max(0.5, cost.getOrDefault(it, 5.0));
    }

    /** Relative drop weight: the harder to get, the (much) smaller. */
    public double weight(Item it) {
        return 100.0 / Math.pow(cost(it), 1.5);
    }
}
