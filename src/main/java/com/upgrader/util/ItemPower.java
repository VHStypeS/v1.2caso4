package com.upgrader.util;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * "How strong / hard to get is this item?" on ONE scale (works on client and server).
 *  - ordinary items: craft difficulty (Complexity: more stages and pricier raw materials = bigger number);
 *  - weapons and tools: sword-equivalent damage D (wood 4, stone 5, iron 6, diamond 7, netherite 8, modded 20-30...):
 *    the bigger the damage, the bigger the rating;
 *  - armor: material power M (defense of all pieces + toughness + knockback resistance): the more protection, the bigger;
 *  - enchanted books: enchantment and level (mending, protection V+ ... are far above Unbreaking I).
 * Bigger rating = drops more rarely.
 */
public final class ItemPower {
    private ItemPower() {}

    public static boolean isBook(ItemStack st) {
        return st.is(Items.ENCHANTED_BOOK);
    }

    /** Sword-equivalent damage, or -1 if the item is not a weapon/tool. */
    public static double weaponDamage(ItemStack st) {
        var it = st.getItem();
        if (it instanceof SwordItem sw) return 1.0 + sw.getDamage();
        if (it instanceof TieredItem ti) return 4.0 + ti.getTier().getAttackDamageBonus();
        double d = 0;
        try {
            Multimap<Attribute, AttributeModifier> mods = st.getAttributeModifiers(EquipmentSlot.MAINHAND);
            for (AttributeModifier m : mods.get(Attributes.ATTACK_DAMAGE)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) d += m.getAmount();
            }
        } catch (Throwable ignored) {
        }
        return d >= 2.0 ? 1.0 + d : -1;
    }

    /** Material power of armor (leather ~7, iron 15, diamond 32, netherite 36), or -1 if it is not armor. */
    public static double armorMaterial(ItemStack st) {
        if (!(st.getItem() instanceof ArmorItem a)) return -1;
        ArmorMaterial m = a.getMaterial();
        double def = 0;
        for (ArmorItem.Type t : ArmorItem.Type.values()) def += m.getDefenseForType(t);
        return def + m.getToughness() * 4.0 + m.getKnockbackResistance() * 40.0;
    }

    private static double slotFactor(ItemStack st) {
        if (st.getItem() instanceof ArmorItem a) {
            return switch (a.getType()) {
                case CHESTPLATE -> 1.0;
                case LEGGINGS -> 0.9;
                case HELMET -> 0.75;
                default -> 0.7;
            };
        }
        return 1.0;
    }

    public static double bookRating(ItemStack st) {
        Map<Enchantment, Integer> m = EnchantmentHelper.getEnchantments(st);
        double r = 0;
        for (Map.Entry<Enchantment, Integer> e : m.entrySet()) {
            Enchantment en = e.getKey();
            double base = en.isTreasureOnly() ? 28 : en.isCurse() ? 2 : 6;
            r += base * Math.pow(1.55, Math.max(0, e.getValue() - 1));
        }
        return Math.max(3, r);
    }

    /** Unified rating: ~1 for dirt, ~10 for iron gear, ~50 for diamond gear, 250+ for netherite and stronger. */
    public static double rating(ItemStack st, @Nullable Level lvl) {
        if (isBook(st)) return bookRating(st);
        double r = Complexity.of(lvl).cost(st.getItem());
        double d = weaponDamage(st);
        if (d > 0) r = Math.max(r, 3.0 * Math.pow(1.25, d));
        double m = armorMaterial(st);
        if (m >= 0) r = Math.max(r, 3.0 * Math.pow(1.12, m) * slotFactor(st));
        return r;
    }
}
