package com.upgrader.service;

import com.upgrader.config.CrateDef;
import com.upgrader.config.CrateEntry;
import com.upgrader.config.CrateManager;
import com.upgrader.config.Messages;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.util.LootRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The only place where a crate is opened. The result is decided here, on the server. */
public final class CrateOpener {
    private CrateOpener() {}

    /**
     * @param consume the crate stack to take one item from (null = free open, e.g. from a command)
     * @return true if the crate was opened
     */
    public static boolean open(ServerPlayer sp, CrateDef def, @Nullable ItemStack consume) {
        CrateManager.Roll roll = CrateManager.roll(def.id, sp.getRandom(), com.upgrader.util.Luck.boost(sp));
        if (roll == null) {
            sp.displayClientMessage(Component.literal("Этот ящик пуст (ещё не настроен).").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (consume != null && !sp.getAbilities().instabuild) {
            consume.shrink(1);
        }

        LootRarity rarity = roll.rarity();
        if (rarity == null) {
            try {
                rarity = LootRarity.auto(roll.stack(), sp.level());
            } catch (Throwable t) {
                rarity = LootRarity.COMMON;
            }
        }

        List<ItemStack> pool = new ArrayList<>();
        List<LootRarity> poolRarity = new ArrayList<>();
        for (CrateEntry e : CrateManager.displayPool(def.id)) {
            ItemStack s = e.displayStack();
            pool.add(s);
            poolRarity.add(e.rarity != null ? e.rarity : LootRarity.fromVanilla(s));
        }
        Network.toPlayer(sp, new Packets.OpenCrate(def.id, def.name, def.rgb(), pool, poolRarity,
                roll.stack().copy(), rarity, roll.percent()));

        // The prize (and the chat message) is handed out only AFTER the roulette ends (see Announcer / OpenDone).
        // Chat gets ONLY legendary-rarity items, without any chances.
        Component msg = null;
        if (rarity == LootRarity.LEGENDARY) {
            msg = Messages.announceLegendary(sp.getGameProfile().getName(), roll.stack(), def, sp.getRandom());
        }
        Announcer.queue(sp, roll.stack().copy(), msg);
        return true;
    }
}
