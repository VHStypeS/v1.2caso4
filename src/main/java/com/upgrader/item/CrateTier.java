package com.upgrader.item;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;

public enum CrateTier {
    COMMON("common", "Common Crate", ChatFormatting.GRAY),
    MID("mid", "Mid Crate", ChatFormatting.GOLD),
    RARE("rare", "Rare Crate", ChatFormatting.AQUA),
    LEGENDARY("legendary", "Legendary Crate", ChatFormatting.YELLOW),
    MYTHIC("mythic", "Mythic Crate", ChatFormatting.RED);

    public final String id;
    public final String displayName;
    public final ChatFormatting color;

    CrateTier(String id, String displayName, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public int rgb() {
        Integer c = color.getColor();
        return c == null ? 0xFFFFFF : c;
    }

    @Nullable
    public static CrateTier byId(String id) {
        for (CrateTier t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
