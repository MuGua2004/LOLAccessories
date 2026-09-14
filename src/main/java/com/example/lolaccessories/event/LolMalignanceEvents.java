package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 残疫（Malignance）·憎恨之雾（Hatefog）。
 *
 * <p>官方口径：终极技能对敌方英雄造成伤害时，在其脚下产生灼烧区域（恨雾），
 * 持续 3 秒：区域内敌人周期性受到魔法伤害，并降低魔法抗性（每目标独立 3 秒冷却）。</p>
 *
 * <p>本模组实现：终极技能（铁魔法基础法力消耗 &gt; 200）施放后开启 3 秒「恨雾窗口」，
 * 窗口内佩戴者造成魔法伤害命中敌人时，在目标脚下生成恨雾（紫色法阵圈界定范围）：
 * 每 0.5 秒对雾内敌人造成 15 + 1.25% 法强的魔法伤害；敌人首次进入雾中降低 10 点
 * 魔法抗性（雾存续期间生效，随雾消散移除）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolMalignanceEvents {

    public static final String GEAR_MALIGNANCE = "malignance";

    /** 恨雾窗口：玩家 UUID → 窗口截止毫秒（终极技能施放后 3 秒）。 */
    private static final Map<UUID, Long> ULT_WINDOW = new HashMap<>();
    /** 恨雾生成冷却：目标 UUID → 下次可生成毫秒（官方：每目标 3 秒）。 */
    private static final Map<UUID, Long> FOG_CD = new HashMap<>();
    /** 魔抗削减标记：目标 UUID → 削减失效毫秒（随雾消散）。 */
    private static final Map<UUID, Long> MR_SHRED = new HashMap<>();
    /** 已激活的恨雾区域（COW：雾的伤害结算可能递归触发新雾生成）。 */
    private static final List<ActiveFog> FOGS = new CopyOnWriteArrayList<>();
    /** 恨雾周期伤害结算中：雾自身伤害不再递归触发新雾生成。 */
    private static boolean fogTickDispatching;

    /** 一片激活中的恨雾。 */
    private static final class ActiveFog {
        final ServerLevel level;
        final ServerPlayer owner;
        final double x;
        final double y;
        final double z;
        final double radius;
        final float baseDamage;
        final float apRatio;
        final String school;
        final long expireMs;
        long nextTickMs;

        ActiveFog(ServerLevel level, ServerPlayer owner, double x, double y, double z,
                  double radius, float baseDamage, float apRatio, String school,
                  long expireMs, long nextTickMs) {
            this.level = level;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.baseDamage = baseDamage;
            this.apRatio = apRatio;
            this.school = school;
            this.expireMs = expireMs;
            this.nextTickMs = nextTickMs;
        }
    }

    private LolMalignanceEvents() {
    }

    /** 由 {@code IronsLegendCastingEvents} 在终极技能施放时调用：开启 3 秒恨雾窗口。 */
    public static void onUltimateCast(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, GEAR_MALIGNANCE)) {
            return;
        }
        GearConfig.OnHitEffect effect = findFogEffect();
        if (effect == null || !effect.enabled) {
            return;
        }
        ULT_WINDOW.put(player.getUUID(), System.currentTimeMillis() + 3000L);
    }

    /**
     * 由 {@code LolNewEpicPassiveEvents.handleAttackerSide} 在魔法伤害命中敌人时调用：
     * 恨雾窗口内 → 在目标脚下生成恨雾（每目标 3 秒冷却）。
     */
    public static void onUltimateDamage(ServerPlayer owner, LivingEntity victim) {
        // 恨雾自身的周期伤害不再递归生成新雾
        if (fogTickDispatching) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long window = ULT_WINDOW.get(owner.getUUID());
        if (window == null || now >= window) {
            return;
        }
        Long cd = FOG_CD.get(victim.getUUID());
        if (cd != null && now < cd) {
            return;
        }
        GearConfig.OnHitEffect effect = findFogEffect();
        if (effect == null || !effect.enabled) {
            return;
        }
        FOG_CD.put(victim.getUUID(), now + 3000L);
        spawnFog(level, owner, victim.getX(), victim.getY(), victim.getZ(), effect);
    }

    private static GearConfig.OnHitEffect findFogEffect() {
        GearConfig config = GearConfigManager.get(GEAR_MALIGNANCE);
        return config == null ? null : config.findEffect("hatefog").orElse(null);
    }

    private static void spawnFog(ServerLevel level, ServerPlayer owner,
                                 double x, double y, double z, GearConfig.OnHitEffect effect) {
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 4.5D;
        long now = System.currentTimeMillis();
        long durationMs = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D) * 1000.0D);
        // 紫色法阵圈界定恨雾范围（地上特效，持续整个雾期；不额外发粒子，仅靠紫圈标示范围）
        LOLNetworking.sendSpotFx(level, FxKind.HATEFOG, x, y, z,
                (int) (durationMs / 50L), (float) radius);
        FOGS.add(new ActiveFog(level, owner, x, y, z, radius,
                (float) (effect.base_damage > 0 ? effect.base_damage : 15.0D),
                (float) (effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.0125D),
                effect.school == null || effect.school.isBlank() ? "eldritch" : effect.school,
                now + durationMs, now + 500L));
    }

    /** 恨雾周期结算（每 0.5 秒一次：周期伤害 + 首次进入降魔抗）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FOGS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        fogTickDispatching = true;
        List<ActiveFog> expired = new ArrayList<>();
        try {
            for (ActiveFog fog : FOGS) {
                if (now >= fog.expireMs || !fog.owner.isAlive()
                        || fog.owner.getServer() == null
                        || fog.owner.getServer().getPlayerList().getPlayer(fog.owner.getUUID()) == null) {
                    expireFog(fog);
                    expired.add(fog);
                    continue;
                }
                if (now < fog.nextTickMs) {
                    continue;
                }
                fog.nextTickMs = now + 500L;
                var school = IronsSpellDamage.resolve(fog.school);
                float damage = (float) (fog.baseDamage + apOf(fog.owner) * fog.apRatio);
                for (LivingEntity e : fog.level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(fog.x, fog.y, fog.z, fog.x, fog.y, fog.z)
                                .inflate(fog.radius, 2.0D, fog.radius),
                        e -> e.isAlive() && e.distanceToSqr(fog.x, fog.y, fog.z) <= fog.radius * fog.radius
                                && LolNewEpicPassiveEvents.isEnemyOf(fog.owner, e))) {
                    IronsSpellDamage.apply(fog.owner, e, damage, school);
                    applyMrShred(e, fog.expireMs);
                }
            }
        } finally {
            fogTickDispatching = false;
        }
        if (!expired.isEmpty()) {
            FOGS.removeAll(expired);
        }
    }

    /** 首次进入恨雾：降低 10 点魔法抗性（雾存续期间生效）。 */
    private static void applyMrShred(LivingEntity victim, long expireMs) {
        long now = System.currentTimeMillis();
        Long until = MR_SHRED.get(victim.getUUID());
        if (until != null && now < until) {
            return; // 已在削减中
        }
        AttributeInstance mr = victim.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
        if (mr == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("hatefog_mr:" + victim.getUUID()).getBytes());
        if (mr.getModifier(id) != null) {
            mr.removeModifier(id);
        }
        mr.addTransientModifier(new AttributeModifier(id, "hatefog_mr_shred", -10.0D,
                AttributeModifier.Operation.ADDITION));
        MR_SHRED.put(victim.getUUID(), expireMs);
    }

    /** 雾消散：移除该场雾标记过的魔抗削减。 */
    private static void expireFog(ActiveFog fog) {
        long now = System.currentTimeMillis();
        MR_SHRED.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            for (ServerPlayer p : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayers()) {
                if (p.level() instanceof ServerLevel level) {
                    var e = level.getEntity(entry.getKey());
                    if (e instanceof LivingEntity living) {
                        AttributeInstance mr = living.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
                        UUID id = UUID.nameUUIDFromBytes(("hatefog_mr:" + entry.getKey()).getBytes());
                        if (mr != null && mr.getModifier(id) != null) {
                            mr.removeModifier(id);
                        }
                    }
                }
            }
            return true;
        });
    }

    private static double apOf(ServerPlayer player) {
        AttributeInstance sp = player.getAttribute(
                net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                        new net.minecraft.resources.ResourceLocation("irons_spellbooks", "spell_power")));
        return sp != null ? sp.getValue() : 0.0D;
    }
}
