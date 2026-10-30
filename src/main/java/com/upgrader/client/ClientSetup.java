package com.upgrader.client;

import com.upgrader.Upgrader;
import com.upgrader.item.CrateItem;
import com.upgrader.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Lets the single "custom_crate" item pick the model of any built-in crate from its NBT ("Style"). */
@Mod.EventBusSubscriber(modid = Upgrader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.CUSTOM_CRATE.get(),
                new ResourceLocation(Upgrader.MODID, "style"),
                (stack, level, entity, seed) -> {
                    CompoundTag t = stack.getTag();
                    return t == null ? 0F : (float) t.getInt(CrateItem.TAG_STYLE);
                }));
    }
}
