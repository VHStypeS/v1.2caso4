package com.upgrader.network;

import com.upgrader.client.ClientHooks;
import com.upgrader.config.Auth;
import com.upgrader.config.CrateDef;
import com.upgrader.config.CrateEntry;
import com.upgrader.config.CrateManager;
import com.upgrader.item.CrateItem;
import com.upgrader.service.Announcer;
import com.upgrader.service.CrateOpener;
import com.upgrader.util.LootRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Packets {
    private static final int MAX_ENTRIES = 4096;

    private Packets() {}

    private static void writeEntries(FriendlyByteBuf b, List<CrateEntry> list) {
        b.writeVarInt(list.size());
        for (CrateEntry e : list) e.write(b);
    }

    private static List<CrateEntry> readEntries(FriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) throw new IllegalArgumentException("Too many entries: " + n);
        List<CrateEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(CrateEntry.read(b));
        return list;
    }

    // ---------------- server -> client ----------------

    /** Open the roulette. The winner was already chosen by the server. */
    public record OpenCrate(String crateId, String name, int rgb, List<ItemStack> pool, List<LootRarity> poolRarity,
                            ItemStack winner, LootRarity rarity, double percent) {
        public static void encode(OpenCrate m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
            b.writeUtf(m.name, 128);
            b.writeInt(m.rgb);
            b.writeVarInt(m.pool.size());
            for (ItemStack s : m.pool) {
                ItemStack c = s.copy();
                c.setCount(1);
                b.writeItem(c);
            }
            for (int i = 0; i < m.pool.size(); i++) {
                b.writeByte(i < m.poolRarity.size() ? m.poolRarity.get(i).ordinal() : 0);
            }
            b.writeByte(m.rarity.ordinal());
            ItemStack w = m.winner.copy();
            w.setCount(1);
            b.writeItem(w);
            b.writeVarInt(m.winner.getCount());
            b.writeDouble(m.percent);
        }

        public static OpenCrate decode(FriendlyByteBuf b) {
            String id = b.readUtf(64);
            String name = b.readUtf(128);
            int rgb = b.readInt();
            int n = b.readVarInt();
            List<ItemStack> pool = new ArrayList<>();
            for (int i = 0; i < n; i++) pool.add(b.readItem());
            List<LootRarity> pr = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LootRarity r = LootRarity.byOrdinal(b.readByte());
                pr.add(r == null ? LootRarity.COMMON : r);
            }
            LootRarity rarity = LootRarity.byOrdinal(b.readByte());
            ItemStack w = b.readItem();
            w.setCount(b.readVarInt());
            double percent = b.readDouble();
            return new OpenCrate(id, name, rgb, pool, pr, w, rarity == null ? LootRarity.COMMON : rarity, percent);
        }

        public static void handle(OpenCrate m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.openCrate(m.crateId, m.name, m.rgb, m.pool, m.poolRarity, m.winner, m.rarity, m.percent)));
            c.get().setPacketHandled(true);
        }
    }

    /** Contents of a crate with the REAL chances (percent). Visible to everybody. boost = active crate-luck in %. */
    public record OpenPreview(String crateId, String name, int rgb, List<CrateEntry> entries, boolean canOpen, double boost) {
        public static OpenPreview of(CrateDef def, boolean canOpen, ServerPlayer viewer) {
            double boost = com.upgrader.util.Luck.boost(viewer);
            return new OpenPreview(def.id, def.name, def.rgb(), CrateManager.preview(def.id, boost, viewer.level()), canOpen, boost);
        }

        public static void encode(OpenPreview m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
            b.writeUtf(m.name, 128);
            b.writeInt(m.rgb);
            writeEntries(b, m.entries);
            b.writeBoolean(m.canOpen);
            b.writeDouble(m.boost);
        }

        public static OpenPreview decode(FriendlyByteBuf b) {
            return new OpenPreview(b.readUtf(64), b.readUtf(128), b.readInt(), readEntries(b), b.readBoolean(), b.readDouble());
        }

        public static void handle(OpenPreview m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.openPreview(m.crateId, m.name, m.rgb, m.entries, m.canOpen, m.boost)));
            c.get().setPacketHandled(true);
        }
    }

    /** The Upgrader block screen: available routes + the chance table (amount -> percent). */
    public record OpenUpgrader(List<Route> routes, List<com.upgrader.config.UpgradeConfig.Point> points, int min, int max) {
        public record Route(String fromId, ItemStack fromIcon, String fromName, int fromRgb,
                            String toId, ItemStack toIcon, String toName, int toRgb) {}

        public static void encode(OpenUpgrader m, FriendlyByteBuf b) {
            b.writeVarInt(m.routes.size());
            for (Route r : m.routes) {
                b.writeUtf(r.fromId, 64);
                b.writeItem(r.fromIcon);
                b.writeUtf(r.fromName, 128);
                b.writeInt(r.fromRgb);
                b.writeUtf(r.toId, 64);
                b.writeItem(r.toIcon);
                b.writeUtf(r.toName, 128);
                b.writeInt(r.toRgb);
            }
            b.writeVarInt(m.points.size());
            for (var p : m.points) {
                b.writeVarInt(p.amount());
                b.writeDouble(p.percent());
            }
            b.writeVarInt(m.min);
            b.writeVarInt(m.max);
        }

        public static OpenUpgrader decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Route> routes = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                routes.add(new Route(b.readUtf(64), b.readItem(), b.readUtf(128), b.readInt(),
                        b.readUtf(64), b.readItem(), b.readUtf(128), b.readInt()));
            }
            int k = b.readVarInt();
            List<com.upgrader.config.UpgradeConfig.Point> pts = new ArrayList<>();
            for (int i = 0; i < k; i++) pts.add(new com.upgrader.config.UpgradeConfig.Point(b.readVarInt(), b.readDouble()));
            return new OpenUpgrader(routes, pts, b.readVarInt(), b.readVarInt());
        }

        public static void handle(OpenUpgrader m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.openUpgrader(m)));
            c.get().setPacketHandled(true);
        }
    }

    public record UpgradeResult(boolean success, double percent, int amount, ItemStack prize, String toName, int toRgb) {
        public static void encode(UpgradeResult m, FriendlyByteBuf b) {
            b.writeBoolean(m.success);
            b.writeDouble(m.percent);
            b.writeVarInt(m.amount);
            b.writeItem(m.prize);
            b.writeUtf(m.toName, 128);
            b.writeInt(m.toRgb);
        }

        public static UpgradeResult decode(FriendlyByteBuf b) {
            return new UpgradeResult(b.readBoolean(), b.readDouble(), b.readVarInt(), b.readItem(), b.readUtf(128), b.readInt());
        }

        public static void handle(UpgradeResult m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.upgradeResult(m)));
            c.get().setPacketHandled(true);
        }
    }

    public record OpenPassword(String crateId) {
        public static void encode(OpenPassword m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
        }

        public static OpenPassword decode(FriendlyByteBuf b) {
            return new OpenPassword(b.readUtf(64));
        }

        public static void handle(OpenPassword m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.openPassword(m.crateId)));
            c.get().setPacketHandled(true);
        }
    }

    public record OpenEditor(String crateId, String name, int rgb, int style, List<CrateEntry> entries) {
        public static void encode(OpenEditor m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
            b.writeUtf(m.name, 128);
            b.writeInt(m.rgb);
            b.writeVarInt(m.style);
            writeEntries(b, m.entries);
        }

        public static OpenEditor decode(FriendlyByteBuf b) {
            return new OpenEditor(b.readUtf(64), b.readUtf(128), b.readInt(), b.readVarInt(), readEntries(b));
        }

        public static OpenEditor of(CrateDef def) {
            List<CrateEntry> list = CrateManager.get(def.id);
            if (list.size() > MAX_ENTRIES) list = new ArrayList<>(list.subList(0, MAX_ENTRIES));
            return new OpenEditor(def.id, def.name, def.rgb(), def.style.ordinal(), list);
        }

        public static void handle(OpenEditor m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.openEditor(m.crateId, m.name, m.rgb, m.style, m.entries)));
            c.get().setPacketHandled(true);
        }
    }

    // ---------------- client -> server ----------------

    /** "Open" button: the server checks the player really holds this crate. */
    public record OpenRequest(String crateId) {
        public static void encode(OpenRequest m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
        }

        public static OpenRequest decode(FriendlyByteBuf b) {
            return new OpenRequest(b.readUtf(64));
        }

        public static void handle(OpenRequest m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp == null) return;
                CrateDef def = CrateManager.def(m.crateId);
                if (def == null) return;
                ItemStack held = sp.getMainHandItem();
                if (!CrateItem.isCrate(held, def.id)) held = sp.getOffhandItem();
                if (!CrateItem.isCrate(held, def.id)) {
                    sp.displayClientMessage(Component.literal("Возьми ящик в руку.").withStyle(ChatFormatting.RED), true);
                    return;
                }
                CrateOpener.open(sp, def, held);
            });
            c.get().setPacketHandled(true);
        }
    }

    public record UpgradeRequest(String fromId, int amount) {
        public static void encode(UpgradeRequest m, FriendlyByteBuf b) {
            b.writeUtf(m.fromId, 64);
            b.writeVarInt(m.amount);
        }

        public static UpgradeRequest decode(FriendlyByteBuf b) {
            return new UpgradeRequest(b.readUtf(64), b.readVarInt());
        }

        public static void handle(UpgradeRequest m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp != null) com.upgrader.service.Upgrading.request(sp, m.fromId, m.amount);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** The roulette finished (or was skipped with ESC): the chat announcement may go out now. */
    public record OpenDone() {
        public static void encode(OpenDone m, FriendlyByteBuf b) {}

        public static OpenDone decode(FriendlyByteBuf b) {
            return new OpenDone();
        }

        public static void handle(OpenDone m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp != null) Announcer.release(sp);
            });
            c.get().setPacketHandled(true);
        }
    }

    public record Password(String crateId, String password) {
        public static void encode(Password m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
            b.writeUtf(m.password, 128);
        }

        public static Password decode(FriendlyByteBuf b) {
            return new Password(b.readUtf(64), b.readUtf(128));
        }

        public static void handle(Password m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp == null || !sp.hasPermissions(2)) return;
                CrateDef def = CrateManager.def(m.crateId);
                if (def == null) return;
                if (Auth.tryLogin(sp, m.password)) {
                    Network.toPlayer(sp, OpenEditor.of(def));
                } else {
                    sp.sendSystemMessage(Component.literal("[Upgrader] Неверный пароль.").withStyle(ChatFormatting.RED));
                    if (Auth.fails(sp.getUUID()) >= 5) {
                        sp.connection.disconnect(Component.literal("Too many wrong passwords"));
                    }
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    /** Saved in chunks: a client packet may not exceed ~32 KB, a big crate does. */
    public record SaveEditor(String crateId, boolean first, boolean last, List<CrateEntry> entries) {
        private static final java.util.Map<java.util.UUID, List<CrateEntry>> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

        public static void encode(SaveEditor m, FriendlyByteBuf b) {
            b.writeUtf(m.crateId, 64);
            b.writeBoolean(m.first);
            b.writeBoolean(m.last);
            writeEntries(b, m.entries);
        }

        public static SaveEditor decode(FriendlyByteBuf b) {
            return new SaveEditor(b.readUtf(64), b.readBoolean(), b.readBoolean(), readEntries(b));
        }

        public static void handle(SaveEditor m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                // Must be OP AND must have entered the password this session.
                if (sp == null || !sp.hasPermissions(2) || !Auth.isAuthed(sp.getUUID())) return;
                CrateDef def = CrateManager.def(m.crateId);
                if (def == null) return;
                List<CrateEntry> acc = m.first ? new ArrayList<>() : PENDING.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
                acc.addAll(m.entries);
                PENDING.put(sp.getUUID(), acc);
                if (!m.last) return;
                PENDING.remove(sp.getUUID());
                CrateManager.set(def.id, acc);
                sp.sendSystemMessage(Component.literal("[Upgrader] Сохранено: " + def.name
                        + " (" + acc.size() + " записей)").withStyle(ChatFormatting.GREEN));
            });
            c.get().setPacketHandled(true);
        }
    }
}
