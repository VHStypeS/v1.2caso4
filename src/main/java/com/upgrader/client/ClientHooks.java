package com.upgrader.client;

import com.upgrader.config.CrateEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Only ever touched through DistExecutor, so the server never loads client classes. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openCrate(String id, String name, int rgb, List<ItemStack> pool, List<com.upgrader.util.LootRarity> poolRarity,
                                 ItemStack winner, com.upgrader.util.LootRarity rarity, double percent) {
        Minecraft.getInstance().setScreen(new CrateScreen(id, name, rgb, pool, poolRarity, winner, rarity, percent));
    }

    public static void openPreview(String id, String name, int rgb, List<CrateEntry> entries, boolean canOpen, double boost) {
        Minecraft.getInstance().setScreen(new PreviewScreen(id, name, rgb, entries, canOpen, boost));
    }

    public static void openUpgrader(com.upgrader.network.Packets.OpenUpgrader m) {
        Minecraft.getInstance().setScreen(new UpgradeScreen(m));
    }

    public static void upgradeResult(com.upgrader.network.Packets.UpgradeResult m) {
        if (Minecraft.getInstance().screen instanceof UpgradeScreen us) us.onResult(m);
    }

    public static void openPassword(String id) {
        Minecraft.getInstance().setScreen(new PasswordScreen(id));
    }

    public static void openEditor(String id, String name, int rgb, int style, List<CrateEntry> entries) {
        com.upgrader.item.CrateTier[] tiers = com.upgrader.item.CrateTier.values();
        com.upgrader.item.CrateTier tier = tiers[Math.max(0, Math.min(tiers.length - 1, style))];
        Minecraft.getInstance().setScreen(new EditorScreen(id, name, rgb, tier, entries));
    }
}
