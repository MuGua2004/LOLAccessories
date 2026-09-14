package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 破败王者之刃（Blade of the Ruined King）的普攻额外伤害与抓挠之影效果。 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolRuinedKingEvents {

    private LolRuinedKingEvents() {
    }

    private static final ThreadLocal<Boolean> IN_CURRENT = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, AttackStreak> CLAW_STREAKS = new HashMap<>();
    private static final long CLAW_STREAK_TIMEOUT_TICKS = 80L;

    private record AttackStreak(UUID targetId, int hits, long lastHitTick) {
    }

    /**
     * 破败王者之刃·电流（Ruined Strike）：佩戴者用近战攻击命中敌人时，额外造成目标
     * 当前生命值 {@code amount}（默认 8%）的物理伤害，至少 15 点。友善/被动生物不生效。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide || IN_CURRENT.get()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // 仅近战（direct == entity，排除弓箭/三叉戟等弹射物）
        if (event.getSource().getEntity() != attacker) {
            return;
        }
        if (!CuriosGearWear.isWearing(attacker, "ruined_king")) {
            return;
        }
        if (!com.example.lolaccessories.event.LolNewEpicPassiveEvents.isEnemyOf(attacker, victim)) {
            return; // 友善/被动生物不生效
        }
        // 抓挠之影与雾之锋独立结算，避免前者受后者数值/开关连带禁用。
        applyClawingShadows(attacker, victim);
        GearConfig.OnHitEffect effect = GearConfigManager.get("ruined_king")
                .findEffect("ruined_king_current").orElse(null);
        if (effect == null || !effect.enabled || effect.amount <= 0.0D) {
            return;
        }
        float bonus = (float) (victim.getHealth() * effect.amount);
        if (bonus < 15.0F) {
            bonus = 15.0F;
        }
        IN_CURRENT.set(true);
        try {
            victim.hurt(attacker.damageSources().playerAttack(attacker), bonus);
        } finally {
            IN_CURRENT.set(false);
        }
    }

    /** 同一目标连续命中 3 次后施加抓挠之影减速，并重新开始下一轮计数。 */
    private static void applyClawingShadows(ServerPlayer attacker, LivingEntity victim) {
        GearConfig.OnHitEffect effect = GearConfigManager.get("ruined_king")
                .findEffect("ruined_king_claw").orElse(null);
        if (effect == null || !effect.enabled || effect.amount <= 0.0D) {
            return;
        }
        long now = attacker.level().getGameTime();
        AttackStreak previous = CLAW_STREAKS.get(attacker.getUUID());
        int hits = previous != null && previous.targetId().equals(victim.getUUID())
                && now - previous.lastHitTick() <= CLAW_STREAK_TIMEOUT_TICKS
                ? previous.hits() + 1 : 1;
        if (hits < 3) {
            CLAW_STREAKS.put(attacker.getUUID(), new AttackStreak(victim.getUUID(), hits, now));
            return;
        }
        CLAW_STREAKS.remove(attacker.getUUID());
        int durationTicks = Math.max(1, (int) Math.round(effect.duration_seconds * 20.0D));
        int amplifier = Math.max(0, (int) Math.round(effect.amount / 0.15D) - 1);
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, durationTicks, amplifier, false, true));
    }
}
