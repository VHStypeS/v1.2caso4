package com.upgrader.config;

import com.upgrader.item.CrateTier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** A crate type: the 5 built-in ones plus custom ones created with /upgrader create. */
public final class CrateDef {
    public final String id;
    public final String name;
    /** Built-in tier whose texture/model/colour this crate uses. */
    public final CrateTier style;
    public final boolean builtin;

    public CrateDef(String id, String name, CrateTier style, boolean builtin) {
        this.id = id;
        this.name = name;
        this.style = style;
        this.builtin = builtin;
    }

    public int rgb() {
        return style.rgb();
    }

    public Component display() {
        return Component.literal(name).withStyle(Style.EMPTY.withColor(rgb()));
    }
}
