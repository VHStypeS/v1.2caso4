package com.upgrader;

import com.mojang.logging.LogUtils;
import com.upgrader.command.UpgraderCommand;
import com.upgrader.config.Auth;
import com.upgrader.config.CrateManager;
import com.upgrader.config.DropConfig;
import com.upgrader.config.Messages;
import com.upgrader.effect.ModEffects;
import com.upgrader.event.DropHandler;
import com.upgrader.brewing.BrewingHandler;
import com.upgrader.brewing.PodkrytkaBrewing;
import com.upgrader.event.LootInjector;
import com.upgrader.item.ModItems;
import com.upgrader.network.Network;
import com.upgrader.service.Announcer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Upgrader.MODID)
public class Upgrader {
    public static final String MODID = "upgrader";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Upgrader() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        com.upgrader.block.ModBlocks.BLOCKS.register(modBus);
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DropHandler());
        MinecraftForge.EVENT_BUS.register(new LootInjector());
        MinecraftForge.EVENT_BUS.register(new BrewingHandler());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Network.register();
            PodkrytkaBrewing.register();
        });
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        UpgraderCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CrateManager.loadAll();
        DropConfig.load();
        Messages.load();
        com.upgrader.config.UpgradeConfig.load();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // do not lose prizes of roulettes that are still spinning
        Announcer.flushAll(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Announcer.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Auth.logout(event.getEntity().getUUID());
        com.upgrader.service.Upgrading.forget(event.getEntity().getUUID());
        if (event.getEntity().getServer() != null) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                Announcer.releaseAll(sp);
            }
        }
    }
}
