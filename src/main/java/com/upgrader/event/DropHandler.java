package com.upgrader.event;

import com.upgrader.config.CrateDef;
import com.upgrader.config.CrateManager;
import com.upgrader.config.DropConfig;
import com.upgrader.effect.ModEffects;
import com.upgrader.item.CrateTier;
import com.upgrader.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Crate drops come ONLY from kills made by a real player's own hands:
 *  - fake players (deployers, turrets, auto-hitters) never count;
 *  - mobs from spawner blocks never drop crates;
 *  - the player must have dealt at least X% of the mob's health himself (lava / fall / cactus / trap kills don't count);
 *  - while +podkrytka is active, after N kills (kill_limit, 65 by default) the chances drop HARD to fixed low values
 *    (no gradual decrease, no full stop).
 * Regular monsters -> common / mid / rare (dropped on the ground).
 * Bosses -> legendary / mythic, straight into the killer's inventory.
 * Potion "+podkrytka": every monster/boss kill has an extra chance (0.1% by default) for a Legendary Crate.
 */
public class DropHandler {
    private static final String TAG_DAMAGE = "upgrader_player_damage";
    private static final String TAG_SPAWNER = "upgrader_from_spawner";

    public static boolean isRealPlayer(ServerPlayer p) {
        return !(p instanceof FakePlayer) && p.getClass() == ServerPlayer.class && p.connection != null;
    }

    @SubscribeEvent
    public void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putBoolean(TAG_SPAWNER, true);
        }
    }

    /** Counts how much damage the PLAYER (and nothing else) dealt to this mob. */
    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) return;
        if (event.getSource().getEntity() instanceof ServerPlayer sp && isRealPlayer(sp)) {
            CompoundTag t = victim.getPersistentData();
            float dealt = Math.min(event.getAmount(), victim.getHealth());
            t.putFloat(TAG_DAMAGE, t.getFloat(TAG_DAMAGE) + Math.max(0F, dealt));
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;

        DropConfig.Data cfg = DropConfig.get();
        if (cfg.requireRealPlayer && !isRealPlayer(killer)) return;

        EntityType<?> type = victim.getType();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (cfg.blacklist.contains(typeId)) return;

        CompoundTag data = victim.getPersistentData();
        if (cfg.blockSpawnerMobs && data.getBoolean(TAG_SPAWNER)) return;
        if (data.getFloat(TAG_DAMAGE) < victim.getMaxHealth() * (cfg.minPlayerDamagePercent / 100.0)) return;

        boolean boss = cfg.bossEntities.contains(typeId) || type.is(Tags.EntityTypes.BOSSES);
        boolean monster = type.getCategory() == MobCategory.MONSTER;
        if (!boss && !monster) return;

        RandomSource rnd = victim.getRandom();

        // +podkrytka: count kills of this potion; past the limit the chances are replaced by the low fixed ones.
        boolean potion = killer.hasEffect(ModEffects.PODKRYTKA.get());
        boolean limited = false;
        if (potion && cfg.potionKillLimit > 0) {
            int kills = KillLimiter.record(killer);
            limited = kills > cfg.potionKillLimit;
            if (kills == cfg.potionKillLimit + 1) {
                killer.sendSystemMessage(Component.literal("+podkrytka: убито слишком много мобов, шансы выпадения резко снижены.")
                        .withStyle(ChatFormatting.RED));
            }
        }

        if (boss) {
            for (Map.Entry<CrateTier, Double> e : cfg.boss.entrySet()) {
                if (roll(rnd, e.getValue())) giveToKiller(killer, victim, e.getKey(), false);
            }
        } else {
            Map<CrateTier, Double> mobChances = limited ? cfg.mobLimited : cfg.mob;
            for (Map.Entry<CrateTier, Double> e : mobChances.entrySet()) {
                if (roll(rnd, e.getValue())) {
                    ItemStack crate = new ItemStack(ModItems.CRATES.get(e.getKey()).get());
                    event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), crate));
                }
            }
        }

        // +podkrytka potion
        if (potion && roll(rnd, limited ? cfg.potionLimitedChance : cfg.potionChance)) {
            giveToKiller(killer, victim, CrateTier.LEGENDARY, true);
        }
    }

    /** A fresh +podkrytka effect (not a refresh of an active one) starts a new kill count. */
    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && event.getOldEffectInstance() == null
                && event.getEffectInstance().getEffect() == ModEffects.PODKRYTKA.get()) {
            KillLimiter.reset(sp);
        }
    }

    private static boolean roll(RandomSource rnd, double percent) {
        return percent > 0 && rnd.nextDouble() * 100.0 < percent;
    }

    private static void giveToKiller(ServerPlayer killer, LivingEntity victim, CrateTier tier, boolean byPotion) {
        CrateDef def = CrateManager.def(tier.id);
        ItemStack crate = def != null ? ModItems.crateStack(def, 1) : new ItemStack(ModItems.CRATES.get(tier).get());
        Component name = crate.getHoverName();
        if (!killer.getInventory().add(crate)) {
            killer.drop(crate, false);
        }
        killer.sendSystemMessage(Component.literal(byPotion ? "+podkrytka сработала! Ты получил " : "Ты получил ")
                .append(name).append("!"));

        boolean announce = tier == CrateTier.MYTHIC || byPotion;
        if (announce && killer.getServer() != null) {
            Component msg = byPotion
                    ? Component.empty().append(killer.getDisplayName()).append(" получил ").append(name)
                    .append(" благодаря ").append(Component.literal("+podkrytka").withStyle(ChatFormatting.LIGHT_PURPLE)).append("!")
                    : Component.empty().append(killer.getDisplayName()).append(" получил ").append(name)
                    .append(" с босса ").append(victim.getDisplayName()).append("!");
            killer.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        }
    }
}
