package com.upgrader.item;

import com.upgrader.config.DropConfig;
import com.upgrader.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Crate-luck potion (crafted on a crafting table). Drink: 2 minutes of better chances for RARE crate items. */
public class LuckPotionItem extends Item {
    private final int amplifier;

    public LuckPotionItem(int amplifier, Properties props) {
        super(props);
        this.amplifier = amplifier;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return amplifier > 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            int ticks = Math.max(1, DropConfig.get().luckSeconds) * 20;
            entity.addEffect(new MobEffectInstance(ModEffects.CRATE_LUCK.get(), ticks, amplifier, false, true, true));
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.upgrader.luck.1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(amplifier > 0 ? "tooltip.upgrader.luck.strong" : "tooltip.upgrader.luck.normal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
