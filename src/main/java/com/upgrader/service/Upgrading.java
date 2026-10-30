package com.upgrader.service;

import com.upgrader.config.CrateDef;
import com.upgrader.config.CrateManager;
import com.upgrader.config.UpgradeConfig;
import com.upgrader.item.CrateItem;
import com.upgrader.item.ModItems;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of the Upgrader block. The client only sends "from crate + amount"; everything is re-checked here. */
public final class Upgrading {
    private static final Map<UUID, Integer> OPENED = new ConcurrentHashMap<>();
    private static final int VALID_TICKS = 20 * 60 * 5;

    private Upgrading() {}

    public static void open(ServerPlayer sp) {
        UpgradeConfig.Data cfg = UpgradeConfig.get();
        List<Packets.OpenUpgrader.Route> routes = new ArrayList<>();
        for (UpgradeConfig.Route r : cfg.routes) {
            CrateDef from = CrateManager.def(r.from());
            CrateDef to = CrateManager.def(r.to());
            if (from == null || to == null) continue;
            routes.add(new Packets.OpenUpgrader.Route(from.id, ModItems.crateStack(from, 1), from.name, from.rgb(),
                    to.id, ModItems.crateStack(to, 1), to.name, to.rgb()));
        }
        if (routes.isEmpty()) {
            sp.displayClientMessage(Component.literal("Апгрейдер не настроен (upgrade.json).").withStyle(ChatFormatting.RED), true);
            return;
        }
        OPENED.put(sp.getUUID(), sp.server.getTickCount());
        Network.toPlayer(sp, new Packets.OpenUpgrader(routes, cfg.points, cfg.minAmount, cfg.maxAmount));
    }

    public static void request(ServerPlayer sp, String fromId, int amount) {
        Integer opened = OPENED.get(sp.getUUID());
        if (opened == null || sp.server.getTickCount() - opened > VALID_TICKS) return;
        UpgradeConfig.Data cfg = UpgradeConfig.get();
        UpgradeConfig.Route route = null;
        for (UpgradeConfig.Route r : cfg.routes) if (r.from().equals(fromId)) route = r;
        CrateDef from = CrateManager.def(fromId);
        CrateDef to = route == null ? null : CrateManager.def(route.to());
        if (from == null || to == null || amount < cfg.minAmount || amount > cfg.maxAmount) return;

        int have = 0;
        for (ItemStack s : sp.getInventory().items) {
            if (CrateItem.isCrate(s, fromId)) have += s.getCount();
        }
        if (have < amount) {
            sp.displayClientMessage(Component.literal("Не хватает ящиков.").withStyle(ChatFormatting.RED), true);
            return;
        }
        int left = amount;
        for (ItemStack s : sp.getInventory().items) {
            if (left <= 0) break;
            if (CrateItem.isCrate(s, fromId)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }

        double percent = UpgradeConfig.chance(cfg.points, amount);
        boolean ok = sp.getRandom().nextDouble() * 100.0 < percent;
        ItemStack prize = ModItems.crateStack(to, cfg.resultAmount);
        if (ok) {
            ItemStack give = prize.copy();
            sp.getInventory().add(give);
            if (!give.isEmpty()) sp.drop(give, false);
        }
        Network.toPlayer(sp, new Packets.UpgradeResult(ok, percent, amount, prize, to.name, to.rgb()));
        sp.inventoryMenu.broadcastChanges();
    }

    public static void forget(UUID id) {
        OPENED.remove(id);
    }
}
