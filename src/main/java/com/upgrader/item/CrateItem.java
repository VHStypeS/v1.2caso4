package com.upgrader.item;

import com.upgrader.config.CrateDef;
import com.upgrader.config.CrateManager;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Built-in crates (tier != null) and the single "custom_crate" item whose identity (id, name, model)
 * is stored in NBT, so new crates can be created at runtime without registering new items.
 */
public class CrateItem extends Item {
    public static final String TAG_ID = "CrateId";
    public static final String TAG_NAME = "CrateName";
    public static final String TAG_STYLE = "Style";

    @Nullable
    private final CrateTier tier;

    public CrateItem(@Nullable CrateTier tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    /** Crate id of any stack, or "" if it is not a crate. */
    public static String crateId(ItemStack s) {
        if (!(s.getItem() instanceof CrateItem ci)) return "";
        if (ci.tier != null) return ci.tier.id;
        CompoundTag t = s.getTag();
        return t == null ? "" : t.getString(TAG_ID);
    }

    public static boolean isCrate(ItemStack s, String id) {
        return !s.isEmpty() && s.getItem() instanceof CrateItem && crateId(s).equals(id);
    }

    private CrateTier styleOf(ItemStack stack) {
        if (tier != null) return tier;
        CompoundTag t = stack.getTag();
        int i = t == null ? 0 : t.getInt(TAG_STYLE);
        CrateTier[] all = CrateTier.values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    @Override
    public Component getName(ItemStack stack) {
        if (tier != null) {
            return Component.translatable(this.getDescriptionId()).withStyle(tier.color);
        }
        CompoundTag t = stack.getTag();
        MutableComponent name = (t != null && t.contains(TAG_NAME))
                ? Component.literal(t.getString(TAG_NAME))
                : Component.translatable(this.getDescriptionId());
        return name.withStyle(Style.EMPTY.withColor(styleOf(stack).rgb()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.upgrader.open").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Keeps the name/model of custom crates in sync when an admin renames the crate. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (tier != null || level.isClientSide || entity.tickCount % 40 != 0) return;
        CrateDef def = CrateManager.def(crateId(stack));
        if (def == null) return;
        CompoundTag t = stack.getOrCreateTag();
        if (!def.name.equals(t.getString(TAG_NAME))) t.putString(TAG_NAME, def.name);
        if (t.getInt(TAG_STYLE) != def.style.ordinal()) t.putInt(TAG_STYLE, def.style.ordinal());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(held);
        }
        CrateDef def = CrateManager.def(crateId(held));
        if (def == null) {
            sp.displayClientMessage(Component.literal("Этот ящик больше не существует.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(held);
        }
        // Right click only SHOWS the contents. Opening happens via the button (OpenRequest packet).
        Network.toPlayer(sp, Packets.OpenPreview.of(def, true, sp));
        sp.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(held);
    }
}
