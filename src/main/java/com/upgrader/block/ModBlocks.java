package com.upgrader.block;

import com.upgrader.Upgrader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Upgrader.MODID);

    public static final RegistryObject<Block> UPGRADER = BLOCKS.register("upgrader", () -> new UpgraderBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 6.0F)
                    .sound(SoundType.METAL).lightLevel(s -> 8).requiresCorrectToolForDrops()));

    private ModBlocks() {}
}
