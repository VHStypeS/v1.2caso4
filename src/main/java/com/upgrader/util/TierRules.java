package com.upgrader.util;

import com.upgrader.item.CrateTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * What a crate of each tier may contain when the editor fills it with random items / a whole mod / random books.
 *   common    : wood ... diamond (and anything of equal damage / armor), cheap items, books up to level II
 *   mid       : iron ... netherite, mid-price items, books up to level IV (mending included)
 *   rare      : iron and better, no upper limit (modded gear too), books of any level (protection V+ with Apotheosis)
 *   legendary : diamond and better;   mythic : netherite and better
 * Gear is judged by damage / armor power, everything else by craft difficulty (see ItemPower).
 * Hand-picked items (the "+ Предмет" picker, "+ Из руки") are NOT restricted - the admin decides.
 */
public final class TierRules {
    private static final double INF = 1e18;
    //                                      common mid    rare   legend mythic
    private static final double[] DMG_MIN = {0,     5.5,   6,     7,     8};
    private static final double[] DMG_MAX = {7.01,  8.01,  INF,   INF,   INF};
    private static final double[] ARM_MIN = {0,     14,    14,    30,    35};
    private static final double[] ARM_MAX = {32.5,  41,    INF,   INF,   INF};
    private static final double[] COST_MIN = {0,    2,     4,     10,    20};
    private static final double[] COST_MAX = {30,   100,   500,   INF,   INF};

    private TierRules() {}

    public static boolean allowed(CrateTier tier, ItemStack st, @Nullable Level lvl) {
        int t = tier.ordinal();
        if (ItemPower.isBook(st)) return bookAllowed(tier, st);
        double d = ItemPower.weaponDamage(st);
        if (d > 0) return d >= DMG_MIN[t] && d <= DMG_MAX[t];
        double m = ItemPower.armorMaterial(st);
        if (m >= 0) return m >= ARM_MIN[t] && m <= ARM_MAX[t];
        double c = Complexity.of(lvl).cost(st.getItem());
        return c >= COST_MIN[t] && c <= COST_MAX[t];
    }

    public static boolean bookAllowed(CrateTier tier, ItemStack st) {
        Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(st);
        if (m.isEmpty()) return false;
        int maxLv = 0;
        boolean treasure = false;
        for (Map.Entry<Enchantment, Integer> e : m.entrySet()) {
            maxLv = Math.max(maxLv, e.getValue());
            treasure |= e.getKey().isTreasureOnly();
        }
        return switch (tier) {
            case COMMON -> maxLv <= 2 && !treasure;
            case MID -> maxLv <= 4;
            case RARE -> true;
            case LEGENDARY -> maxLv >= 3 || treasure;
            case MYTHIC -> maxLv >= 4 || treasure;
        };
    }

    /** Short human-readable description for the editor header. */
    public static String describe(CrateTier tier) {
        return switch (tier) {
            case COMMON -> "дерево → алмаз (урон ≤ 7, броня ≤ алмаз), книги до ур. II";
            case MID -> "железо → незерит, книги до ур. IV (+починка)";
            case RARE -> "от железа и выше, без потолка, книги любого уровня";
            case LEGENDARY -> "от алмаза и выше, сильные книги";
            case MYTHIC -> "от незерита и выше, топовые книги";
        };
    }
}
