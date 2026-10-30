package com.upgrader.client;

import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public class PasswordScreen extends Screen {
    private final String crateId;
    private EditBox box;

    public PasswordScreen(String crateId) {
        super(Component.literal("Upgrader"));
        this.crateId = crateId;
    }

    @Override
    protected void init() {
        box = new EditBox(font, width / 2 - 80, height / 2 - 10, 160, 20, Component.empty());
        box.setMaxLength(64);
        box.setFormatter((s, i) -> FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new FancyButton(width / 2 - 50, height / 2 + 16, 100, 20, Component.literal("Войти"), b -> submit(), 0x55FF77));
    }

    private void submit() {
        Network.CHANNEL.sendToServer(new Packets.Password(crateId, box.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.backdrop(g, width, height, 0x55FF77);
        Ui.panel(g, width / 2 - 120, height / 2 - 44, 240, 96, 0x55FF77);
        g.drawCenteredString(font, "Редактор ящика «" + crateId + "» — введи пароль", width / 2, height / 2 - 30, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
