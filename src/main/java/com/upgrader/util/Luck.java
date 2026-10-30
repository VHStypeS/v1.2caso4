package com.upgrader.util;

import com.upgrader.config.DropConfig;
import com.upgrader.effect.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class Luck {
    private Luck() {}

    /** Relative boost (in percent) of rare items for this player right now; 0 without the potion. */
    public static double boost(ServerPlayer sp) {
        MobEffectInstance inst = sp.getEffect(ModEffects.CRATE_LUCK.get());
        if (inst == null) return 0;
        DropConfig.Data cfg = DropConfig.get();
        return inst.getAmplifier() >= 1 ? cfg.luckBoostGreater : cfg.luckBoost;
    }
}
