package com.upgrader.brewing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.brewing.PotionBrewEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** After a brew: takes the extra ingredient items (3 diamond blocks, 2 netherite, 32 gunpowder) and cleans the marker. */
public class BrewingHandler {

    @SubscribeEvent
    public void onBrewed(PotionBrewEvent.Post event) {
        int extra = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack s = event.getItem(i);
            CompoundTag t = s.getTag();
            if (t != null && t.contains(PodkrytkaBrewing.TAG_FRESH)) {
                extra = Math.max(extra, t.getInt(PodkrytkaBrewing.TAG_FRESH));
                t.remove(PodkrytkaBrewing.TAG_FRESH);
                if (t.isEmpty()) s.setTag(null);
            }
        }
        if (extra > 0) {
            event.getItem(3).shrink(extra); // vanilla itself takes the first item
        }
    }
}
