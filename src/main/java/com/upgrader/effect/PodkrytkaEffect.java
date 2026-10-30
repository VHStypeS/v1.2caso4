package com.upgrader.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect "+podkrytka". The logic lives in DropHandler: while it is active, kills can drop a Legendary Crate. */
public class PodkrytkaEffect extends MobEffect {
    public PodkrytkaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF55E0);
    }
}
