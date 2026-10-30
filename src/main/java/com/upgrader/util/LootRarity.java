package com.upgrader.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Rarity class of an item INSIDE a crate (not the vanilla one). Only LEGENDARY items are announced in chat. */
public enum LootRarity {
    COMMON("common", "Обычный", 0x7CFF7C),
    UNCOMMON("uncommon", "Необычный", 0x4FC3FF),
    RARE("rare", "Редкий", 0xB26BFF),
    EPIC("epic", "Эпический", 0xFF8A3B),
    LEGENDARY("legendary", "Легендарный", 0xFFD83B);

    public final String id;
    public final String ru;
    public final int rgb;

    LootRarity(String id, String ru, int rgb) {
        this.id = id;
        this.ru = ru;
        this.rgb = rgb;
    }

    @Nullable
    public static LootRarity byId(String id) {
        for (LootRarity r : values()) if (r.id.equalsIgnoreCase(id)) return r;
        return null;
    }

    @Nullable
    public static LootRarity byOrdinal(int i) {
        return i < 0 || i >= values().length ? null : values()[i];
    }

    public LootRarity next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Absolute scale by the unified item rating (see ItemPower.rating). */
    public static LootRarity byRating(double r) {
        if (r < 8) return COMMON;
        if (r < 25) return UNCOMMON;
        if (r < 80) return RARE;
        if (r < 250) return EPIC;
        return LEGENDARY;
    }

    /** Full auto classification (craft difficulty, damage, armor, enchantments). Needs recipes of the level. */
    public static LootRarity auto(ItemStack st, @Nullable Level level) {
        return byRating(ItemPower.rating(st, level));
    }

    /** Cheap decorative guess (used for the roulette ring items): vanilla rarity of the item. */
    public static LootRarity fromVanilla(ItemStack st) {
        return switch (st.getItem().getRarity(st)) {
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
            default -> COMMON;
        };
    }
}
