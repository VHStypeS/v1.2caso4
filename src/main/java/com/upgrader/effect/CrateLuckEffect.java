package com.upgrader.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect: raises the chance of RARE items when opening crates (see Luck / CrateManager.roll). */
public class CrateLuckEffect extends MobEffect {
    public CrateLuckEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x55FF9A);
    }
}
