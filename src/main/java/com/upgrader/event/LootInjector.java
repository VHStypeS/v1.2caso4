package com.upgrader.event;

import com.upgrader.config.DropConfig;
import com.upgrader.item.CrateTier;
import com.upgrader.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

/**
 * Adds Common / Mid crates (and ONLY those two tiers) to dungeon chests.
 * Tables and chances: drops.json -> dungeon_loot_tables / dungeon_chest_chance_percent.
 * A table pattern may end with '*' (e.g. "somemod:chests/*"). Changes need a world restart or /reload.
 */
public class LootInjector {

    @SubscribeEvent
    public void onLootTable(LootTableLoadEvent event) {
        DropConfig.Data cfg = DropConfig.get();
        if (!matches(event.getName(), cfg)) return;

        for (Map.Entry<CrateTier, Double> e : cfg.dungeon.entrySet()) {
            double percent = e.getValue();
            if (percent <= 0) continue;
            if (e.getKey() != CrateTier.COMMON && e.getKey() != CrateTier.MID) continue;
            Item crate = ModItems.CRATES.get(e.getKey()).get();
            event.getTable().addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance((float) (percent / 100.0)))
                    .add(LootItem.lootTableItem(crate))
                    .build());
        }
    }

    private static boolean matches(ResourceLocation name, DropConfig.Data cfg) {
        String n = name.toString();
        for (String p : cfg.dungeonTables) {
            if (p.endsWith("*") ? n.startsWith(p.substring(0, p.length() - 1)) : n.equals(p)) return true;
        }
        return false;
    }
}
