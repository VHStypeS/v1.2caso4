package com.upgrader.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Counts kills made while the +podkrytka effect is active (one counter per potion "session").
 * Stored in the player's persistent data, so relogging does not reset it.
 * The counter is reset when the effect is applied from scratch (see DropHandler.onEffectAdded).
 */
public final class KillLimiter {
    private static final String TAG = "upgrader_potion_kills";

    private KillLimiter() {}

    /** Counts one more kill and returns the new total for the current potion. */
    public static int record(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        int n = t.getInt(TAG) + 1;
        t.putInt(TAG, n);
        return n;
    }

    public static void reset(ServerPlayer p) {
        p.getPersistentData().remove(TAG);
    }
}
