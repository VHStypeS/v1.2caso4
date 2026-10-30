package com.upgrader.service;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the PRIZE (and the optional chat announcement) of every opened crate until the roulette animation
 * is over: the client sends OpenDone when it finishes, is skipped with ESC, or the screen is closed.
 * Only then does the item land in the inventory, so nothing is spoiled and nothing drops early.
 * A fallback timer hands it out anyway if the client never reports back; logout / server stop hand it out too.
 */
public final class Announcer {
    private static final int FALLBACK_TICKS = 15 * 20;

    private record Pending(ItemStack prize, @Nullable Component msg, int deadline) {}

    private static final Map<UUID, Deque<Pending>> QUEUES = new ConcurrentHashMap<>();

    private Announcer() {}

    /** Called when a crate is opened: the prize is kept here until the animation ends. */
    public static void queue(ServerPlayer sp, ItemStack prize, @Nullable Component msg) {
        MinecraftServer server = sp.getServer();
        if (server == null) {
            grant(sp, prize);
            return;
        }
        Deque<Pending> q = QUEUES.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(new Pending(prize, msg, server.getTickCount() + FALLBACK_TICKS));
        }
    }

    /** The animation of the oldest pending open finished. */
    public static void release(ServerPlayer sp) {
        Deque<Pending> q = QUEUES.get(sp.getUUID());
        if (q == null) return;
        Pending p;
        synchronized (q) {
            p = q.pollFirst();
        }
        if (p != null) finish(sp, p);
    }

    public static void releaseAll(ServerPlayer sp) {
        Deque<Pending> q = QUEUES.remove(sp.getUUID());
        if (q == null) return;
        synchronized (q) {
            for (Pending p : q) finish(sp, p);
            q.clear();
        }
    }

    /** Server is stopping: give everything out now. */
    public static void flushAll(MinecraftServer server) {
        for (UUID id : QUEUES.keySet().toArray(new UUID[0])) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) releaseAll(sp);
        }
        QUEUES.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || QUEUES.isEmpty()) return;
        int now = server.getTickCount();
        for (Map.Entry<UUID, Deque<Pending>> e : QUEUES.entrySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            Deque<Pending> q = e.getValue();
            synchronized (q) {
                while (!q.isEmpty() && q.peekFirst().deadline() <= now) {
                    Pending p = q.pollFirst();
                    if (sp != null) finish(sp, p);
                }
            }
        }
    }

    private static void finish(ServerPlayer sp, Pending p) {
        grant(sp, p.prize());
        if (p.msg() != null && sp.getServer() != null) {
            sp.getServer().getPlayerList().broadcastSystemMessage(p.msg(), false);
        }
    }

    private static void grant(ServerPlayer sp, ItemStack prize) {
        ItemStack give = prize.copy();
        sp.getInventory().add(give);
        if (!give.isEmpty()) {
            sp.drop(give, false);
        }
    }
}
