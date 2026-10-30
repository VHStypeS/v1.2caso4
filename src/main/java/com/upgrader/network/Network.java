package com.upgrader.network;

import com.upgrader.Upgrader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Network {
    private static final String VERSION = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Upgrader.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, Packets.OpenCrate.class, Packets.OpenCrate::encode, Packets.OpenCrate::decode,
                Packets.OpenCrate::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.OpenPreview.class, Packets.OpenPreview::encode, Packets.OpenPreview::decode,
                Packets.OpenPreview::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.OpenUpgrader.class, Packets.OpenUpgrader::encode, Packets.OpenUpgrader::decode,
                Packets.OpenUpgrader::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.UpgradeResult.class, Packets.UpgradeResult::encode, Packets.UpgradeResult::decode,
                Packets.UpgradeResult::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.UpgradeRequest.class, Packets.UpgradeRequest::encode, Packets.UpgradeRequest::decode,
                Packets.UpgradeRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.OpenPassword.class, Packets.OpenPassword::encode, Packets.OpenPassword::decode,
                Packets.OpenPassword::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.OpenEditor.class, Packets.OpenEditor::encode, Packets.OpenEditor::decode,
                Packets.OpenEditor::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Packets.OpenRequest.class, Packets.OpenRequest::encode, Packets.OpenRequest::decode,
                Packets.OpenRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.OpenDone.class, Packets.OpenDone::encode, Packets.OpenDone::decode,
                Packets.OpenDone::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.Password.class, Packets.Password::encode, Packets.Password::decode,
                Packets.Password::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Packets.SaveEditor.class, Packets.SaveEditor::encode, Packets.SaveEditor::decode,
                Packets.SaveEditor::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
