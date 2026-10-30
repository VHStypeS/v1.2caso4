package com.upgrader.config;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import com.upgrader.util.LootRarity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** One line in a crate: item (+ optional NBT), count range and chance in percent. */
public final class CrateEntry {
    public static final int MAX_COUNT = 4096;

    public final ResourceLocation id;
    @Nullable
    public final CompoundTag tag;
    public final int countMin;
    public final int countMax;
    public final double chance;
    /** Rarity class inside the crate (only LEGENDARY is announced in chat). null = old entry, classified automatically. */
    @Nullable
    public final LootRarity rarity;

    public CrateEntry(ResourceLocation id, @Nullable CompoundTag tag, int countMin, int countMax, double chance) {
        this(id, tag, countMin, countMax, chance, null);
    }

    public CrateEntry(ResourceLocation id, @Nullable CompoundTag tag, int countMin, int countMax, double chance,
                      @Nullable LootRarity rarity) {
        this.id = id;
        this.tag = (tag == null || tag.isEmpty()) ? null : tag;
        int mn = Mth.clamp(countMin, 1, MAX_COUNT);
        this.countMin = mn;
        this.countMax = Mth.clamp(countMax, mn, MAX_COUNT);
        this.chance = (Double.isNaN(chance) || chance < 0) ? 0 : Math.min(chance, 1_000_000);
        this.rarity = rarity;
    }

    public CrateEntry withChance(double newChance) {
        return new CrateEntry(id, tag, countMin, countMax, newChance, rarity);
    }

    /** Explicit rarity, or an automatic one (craft difficulty / damage / armor / enchantments). */
    public LootRarity resolveRarity(@Nullable Level level) {
        if (rarity != null) return rarity;
        try {
            return LootRarity.auto(displayStack(), level);
        } catch (Throwable t) {
            return LootRarity.COMMON;
        }
    }

    /** False when the item's mod is not installed. */
    public boolean isAvailable() {
        if (!ForgeRegistries.ITEMS.containsKey(id)) return false;
        return ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
    }

    public ItemStack stack(int count) {
        if (!isAvailable()) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        ItemStack s = new ItemStack(item, count);
        if (tag != null) s.setTag(tag.copy());
        return s;
    }

    /** Icon for GUIs; a barrier if the item does not exist. */
    public ItemStack displayStack() {
        ItemStack s = stack(1);
        return s.isEmpty() ? new ItemStack(Items.BARRIER) : s;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("item", id.toString());
        if (tag != null) o.addProperty("nbt", tag.toString());
        o.addProperty("count_min", countMin);
        o.addProperty("count_max", countMax);
        o.addProperty("chance", chance);
        if (rarity != null) o.addProperty("rarity", rarity.id);
        return o;
    }

    public static CrateEntry fromJson(JsonObject o) throws Exception {
        ResourceLocation id = new ResourceLocation(o.get("item").getAsString());
        CompoundTag tag = o.has("nbt") ? TagParser.parseTag(o.get("nbt").getAsString()) : null;
        int min = o.has("count_min") ? o.get("count_min").getAsInt() : 1;
        int max = o.has("count_max") ? o.get("count_max").getAsInt() : min;
        double chance = o.get("chance").getAsDouble();
        LootRarity rar = o.has("rarity") ? LootRarity.byId(o.get("rarity").getAsString()) : null;
        return new CrateEntry(id, tag, min, max, chance, rar);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeBoolean(tag != null);
        if (tag != null) buf.writeNbt(tag);
        buf.writeVarInt(countMin);
        buf.writeVarInt(countMax);
        buf.writeDouble(chance);
        buf.writeByte(rarity == null ? -1 : rarity.ordinal());
    }

    public static CrateEntry read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        CompoundTag tag = buf.readBoolean() ? buf.readNbt() : null;
        int min = buf.readVarInt();
        int max = buf.readVarInt();
        double chance = buf.readDouble();
        LootRarity rar = LootRarity.byOrdinal(buf.readByte());
        return new CrateEntry(id, tag, min, max, chance, rar);
    }
}
