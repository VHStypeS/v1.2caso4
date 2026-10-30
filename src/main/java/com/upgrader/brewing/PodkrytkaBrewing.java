package com.upgrader.brewing;

import com.upgrader.Upgrader;
import com.upgrader.config.DropConfig;
import com.upgrader.effect.ModEffects;
import com.upgrader.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Brewing stand chain for +podkrytka (bottles at the bottom, ingredient on top):
 *   water bottle        + fermented spider eye          -> stage 1
 *   stage 1             + 3 diamond blocks (one go)     -> stage 2
 *   stage 2             + 2 netherite ingots (one go)   -> +podkrytka (1 minute)
 *   +podkrytka          + 32 gunpowder (one go)         -> splash +podkrytka
 * Counts are in drops.json (podkrytka.brew_*). The extra items are taken in BrewingHandler after the brew.
 * Stage potions are vanilla potions with a tag, so the brewing stand accepts them everywhere.
 */
public final class PodkrytkaBrewing implements IBrewingRecipe {
    public static final String TAG_STAGE = "upgrader_brew_stage";
    /** On a fresh output: how many MORE ingredient items (besides the 1 vanilla takes) must be used up. */
    public static final String TAG_FRESH = "upgrader_brew_fresh";

    private static final int COLOR_STAGE_1 = 0x6B3FA0;
    private static final int COLOR_STAGE_2 = 0x3FD6E0;
    private static final int COLOR_PODKRYTKA = 0xFF55E0;

    /**
     * Registered FIRST: vanilla already has "water bottle + fermented spider eye = Weakness" and would win otherwise.
     * (So Weakness can no longer be brewed straight from a water bottle.)
     */
    @SuppressWarnings("unchecked")
    public static void register() {
        IBrewingRecipe recipe = new PodkrytkaBrewing();
        try {
            Field f = BrewingRecipeRegistry.class.getDeclaredField("recipes");
            f.setAccessible(true);
            ((List<IBrewingRecipe>) f.get(null)).add(0, recipe);
        } catch (Throwable t) {
            Upgrader.LOGGER.warn("[Upgrader] Cannot put the brewing recipe first, adding it last "
                    + "(the first step will then give Weakness instead)", t);
            BrewingRecipeRegistry.addRecipe(recipe);
        }
    }

    private static boolean isWater(ItemStack s) {
        return s.is(Items.POTION) && PotionUtils.getPotion(s) == Potions.WATER;
    }

    private static int stageOf(ItemStack s) {
        return s.is(Items.POTION) && s.hasTag() ? s.getTag().getInt(TAG_STAGE) : 0;
    }

    @Override
    public boolean isInput(ItemStack input) {
        return isWater(input) || stageOf(input) > 0 || input.is(ModItems.PODKRYTKA.get());
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(Items.FERMENTED_SPIDER_EYE) || ingredient.is(Items.DIAMOND_BLOCK)
                || ingredient.is(Items.NETHERITE_INGOT) || ingredient.is(Items.GUNPOWDER);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || ingredient.isEmpty()) return ItemStack.EMPTY;
        DropConfig.Data cfg = DropConfig.get();

        if (isWater(input) && ingredient.is(Items.FERMENTED_SPIDER_EYE)) {
            return stagePotion(1);
        }
        int st = stageOf(input);
        if (st == 1 && ingredient.is(Items.DIAMOND_BLOCK) && ingredient.getCount() >= cfg.brewDiamondBlocks) {
            return fresh(stagePotion(2), cfg.brewDiamondBlocks);
        }
        if (st == 2 && ingredient.is(Items.NETHERITE_INGOT) && ingredient.getCount() >= cfg.brewNetherite) {
            return fresh(new ItemStack(ModItems.PODKRYTKA.get()), cfg.brewNetherite);
        }
        if (input.is(ModItems.PODKRYTKA.get()) && ingredient.is(Items.GUNPOWDER)
                && ingredient.getCount() >= cfg.brewGunpowder) {
            return fresh(splashPodkrytka(), cfg.brewGunpowder);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack fresh(ItemStack s, int need) {
        if (need > 1) s.getOrCreateTag().putInt(TAG_FRESH, need - 1);
        return s;
    }

    private static ItemStack stagePotion(int stage) {
        ItemStack s = new ItemStack(Items.POTION);
        CompoundTag t = s.getOrCreateTag();
        t.putInt(TAG_STAGE, stage);
        int color = stage == 1 ? COLOR_STAGE_1 : COLOR_STAGE_2;
        t.putInt("CustomPotionColor", color);
        s.setHoverName(Component.literal("Основа +podkrytka (" + stage + "/2)")
                .withStyle(Style.EMPTY.withItalic(false).withColor(color)));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(stage == 1
                        ? "Дальше: алмазные блоки"
                        : "Дальше: слитки незерита")
                .withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    /** A vanilla splash potion carrying the +podkrytka effect: vanilla handles throwing and the area. */
    private static ItemStack splashPodkrytka() {
        ItemStack s = new ItemStack(Items.SPLASH_POTION);
        int ticks = Math.max(1, DropConfig.get().potionSeconds) * 20;
        PotionUtils.setCustomEffects(s, List.of(
                new MobEffectInstance(ModEffects.PODKRYTKA.get(), ticks, 0, false, true, true)));
        s.getOrCreateTag().putInt("CustomPotionColor", COLOR_PODKRYTKA);
        s.setHoverName(Component.literal("+podkrytka (взрывное)")
                .withStyle(Style.EMPTY.withItalic(false).withColor(COLOR_PODKRYTKA)));
        return s;
    }
}
