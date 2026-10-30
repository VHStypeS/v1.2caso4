package com.upgrader.effect;

import com.upgrader.Upgrader;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Upgrader.MODID);

    public static final RegistryObject<MobEffect> PODKRYTKA = EFFECTS.register("podkrytka", PodkrytkaEffect::new);

    public static final RegistryObject<MobEffect> CRATE_LUCK = EFFECTS.register("crate_luck", CrateLuckEffect::new);

    private ModEffects() {}
}
