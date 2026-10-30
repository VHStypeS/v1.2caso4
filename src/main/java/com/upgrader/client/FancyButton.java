package com.upgrader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat gradient button with an accent-coloured border and a glow on hover. */
public class FancyButton extends Button {
    private final int accent;

    public FancyButton(int x, int y, int w, int h, Component msg, OnPress onPress, int accent) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = active && isHoveredOrFocused();
        int x = getX(), y = getY();
        if (active) {
            int top = Ui.mix(0x14142A, accent, hov ? 0.55 : 0.30);
            int bot = Ui.mix(0x0A0A16, accent, hov ? 0.30 : 0.10);
            g.fillGradient(x, y, x + width, y + height, Ui.argb(0xF0, top), Ui.argb(0xF0, bot));
            g.renderOutline(x, y, width, height, Ui.argb(hov ? 0xFF : 0xAA, accent));
            if (hov) g.fill(x + 1, y + 1, x + width - 1, y + 2, Ui.argb(0x99, 0xFFFFFF));
        } else {
            g.fillGradient(x, y, x + width, y + height, 0xE0202028, 0xE0121218);
            g.renderOutline(x, y, width, height, 0xFF3A3A48);
        }
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + (height - 8) / 2,
                active ? 0xFFFFFF : 0x777788);
    }
}
