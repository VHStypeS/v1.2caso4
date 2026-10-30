package com.upgrader.item;

import com.upgrader.Upgrader;
import com.upgrader.config.CrateDef;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Upgrader.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Upgrader.MODID);

    public static final Map<CrateTier, RegistryObject<Item>> CRATES = new EnumMap<>(CrateTier.class);

    static {
        for (CrateTier tier : CrateTier.values()) {
            CRATES.put(tier, ITEMS.register(tier.id + "_crate",
                    () -> new CrateItem(tier, new Item.Properties().stacksTo(64))));
        }
    }

    /** One item for ALL custom crates: id, name and model are in the stack's NBT. */
    public static final RegistryObject<Item> CUSTOM_CRATE = ITEMS.register("custom_crate",
            () -> new CrateItem(null, new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> PODKRYTKA = ITEMS.register("podkrytka",
            () -> new PodkrytkaPotionItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> LUCK_POTION = ITEMS.register("luck_potion",
            () -> new LuckPotionItem(0, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LUCK_POTION_GREATER = ITEMS.register("luck_potion_greater",
            () -> new LuckPotionItem(1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UPGRADER_BLOCK = ITEMS.register("upgrader",
            () -> new net.minecraft.world.item.BlockItem(com.upgrader.block.ModBlocks.UPGRADER.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Upgrader"))
                    .icon(() -> new ItemStack(CRATES.get(CrateTier.MYTHIC).get()))
                    .displayItems((params, out) -> {
                        CRATES.values().forEach(r -> out.accept(r.get()));
                        out.accept(PODKRYTKA.get());
                        out.accept(LUCK_POTION.get());
                        out.accept(LUCK_POTION_GREATER.get());
                        out.accept(UPGRADER_BLOCK.get());
                    })
                    .build());

    private ModItems() {}

    /** The item stack for any crate (built-in or custom). */
    public static ItemStack crateStack(CrateDef def, int count) {
        if (def.builtin) {
            return new ItemStack(CRATES.get(def.style).get(), count);
        }
        ItemStack s = new ItemStack(CUSTOM_CRATE.get(), count);
        CompoundTag t = s.getOrCreateTag();
        t.putString(CrateItem.TAG_ID, def.id);
        t.putString(CrateItem.TAG_NAME, def.name);
        t.putInt(CrateItem.TAG_STYLE, def.style.ordinal());
        return s;
    }
}
