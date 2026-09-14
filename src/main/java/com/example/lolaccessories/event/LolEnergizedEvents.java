package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 盈能（Energized）系统：移动与普攻命中积攒能量，攒满后下一次普攻为「盈能攻击」，
 * 触发全部已佩戴盈能装备的效果并清空能量（能量池共享，多件装备同时触发——
 * 与咒刃叠加同思路：一次触发结算全部，不重复占用）。
 *
 * <p>当前盈能装备：</p>
 * <ul>
 *   <li><b>斯塔缇克电刃</b>：对目标及附近至多 6 名敌人施放闪电链（闪电学派魔法伤害，
 *       逐跳传导，真实闪电视觉特效）；</li>
 *   <li><b>疾射火炮</b>：盈能攻击附加 120 点火焰魔法伤害。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolEnergizedEvents {

    public static final String GEAR_STATIKK = "statikk_shiv";
    public static final String GEAR_FIRECANNON = "rapid_firecannon";
    public static final String GEAR_STORMRAZOR = "stormrazor";

    /** 能量上限。 */
    private static final float MAX_ENERGY = 100.0F;
    /** 移动积攒：每移动 1 格 +0.5（即每 2 格积攒 1% 进度）。 */
    private static final float MOVE_ENERGY_PER_BLOCK = 0.5F;
    /** 普攻命中积攒：每次攻击 +5% 进度（1 件盈能装备的基准值）。 */
    private static final float HIT_ENERGY = 5.0F;
    /** 判定「移动中」的位移阈值。 */
    private static final double MOVE_EPSILON = 0.005D;

    /** 玩家能量池（0~100）。 */
    private static final Map<UUID, Float> ENERGY = new HashMap<>();
    /** 上一 tick 位置（判定移动）。 */
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

    private LolEnergizedEvents() {
    }

    private static boolean hasEnergizedGear(Player player) {
        return energizedGearCount(player) > 0;
    }

    /** 佩戴的盈能装备件数。 */
    private static int energizedGearCount(Player player) {
        int[] count = {0};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            String id = gear.getGearId();
            if (GEAR_STATIKK.equals(id) || GEAR_FIRECANNON.equals(id) || GEAR_STORMRAZOR.equals(id)) {
                count[0]++;
            }
        });
        return count[0];
    }

    /**
     * 多件倍率：每多一件盈能装备，内部积攒速度翻倍（1 件 ×1、2 件 ×2、3 件 ×4……）。
     * 只放大内部能量数值——盈能条 HUD 按倍率规范化显示，条长度观感不变。
     */
    private static float energizedMultiplier(ServerPlayer player) {
        int count = energizedGearCount(player);
        return count <= 1 ? 1.0F : (float) Math.pow(2.0D, count - 1);
    }

    // ------------------------------------------------------------------ //
    // 移动积攒
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!hasEnergizedGear(player)) {
            ENERGY.remove(player.getUUID());
            LAST_POS.remove(player.getUUID());
            return;
        }
        double[] last = LAST_POS.get(player.getUUID());
        double[] cur = {player.getX(), player.getZ()};
        LAST_POS.put(player.getUUID(), cur);
        if (last != null) {
            double moved = Math.abs(cur[0] - last[0]) + Math.abs(cur[1] - last[1]);
            if (moved > MOVE_EPSILON) {
                addEnergy(player, (float) (moved * MOVE_ENERGY_PER_BLOCK));
            }
        }
        // 盈能条 HUD：每秒兜底同步一次（含登入后对齐）
        if (player.tickCount % 20 == 0) {
            syncEnergy(player, ENERGY.getOrDefault(player.getUUID(), 0.0F));
        }
    }

    /** 积攒能量（内部数值按多件倍率放大）。返回积攒后的能量值。 */
    private static float addEnergy(ServerPlayer player, float delta) {
        float gained = delta * energizedMultiplier(player);
        float e = Math.min(MAX_ENERGY, ENERGY.getOrDefault(player.getUUID(), 0.0F) + gained);
        ENERGY.put(player.getUUID(), e);
        syncEnergy(player, e);
        return e;
    }

    /** 上次同步到客户端的能量值（变化 ≥2 或清零才发包，避免每 tick 刷包）。 */
    private static final Map<UUID, Float> LAST_SYNC = new HashMap<>();

    /**
     * 盈能条 HUD 同步：内部能量按多件倍率规范化后发送，
     * 客户端条长度只反映「百分比进度」，不随倍率变快而跳变。
     */
    private static void syncEnergy(ServerPlayer player, float e) {
        float display = e / energizedMultiplier(player);
        Float last = LAST_SYNC.get(player.getUUID());
        if (last == null || Math.abs(last - display) >= 2.0F || (last > 0.0F && display <= 0.0F)) {
            LAST_SYNC.put(player.getUUID(), display);
            LOLNetworking.sendEnergySync(player, display, MAX_ENERGY);
        }
    }

    // ------------------------------------------------------------------ //
    // 普攻命中：积攒 + 满能量触发盈能攻击
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 只有普攻（近战直击/弹射物）积攒与触发；铁魔法等魔法不触发
        boolean basic = event.getSource().getDirectEntity() == player
                || event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
        if (!basic || !LolNewEpicPassiveEvents.isEnemyOf(player, victim)) {
            return;
        }
        if (!hasEnergizedGear(player)) {
            return;
        }
        float e = addEnergy(player, HIT_ENERGY);
        if (e >= MAX_ENERGY) {
            ENERGY.put(player.getUUID(), 0.0F);
            triggerEnergized(player, victim);
        }
    }

    /** 盈能攻击：结算全部已佩戴盈能装备的效果。 */
    private static void triggerEnergized(ServerPlayer player, LivingEntity victim) {
        syncEnergy(player, 0.0F);
        List<String> gears = new ArrayList<>();
        CuriosGearWear.forEachEquippedGear(player, gear -> gears.add(gear.getGearId()));
        if (gears.contains(GEAR_STATIKK)) {
            statikkChain(player, victim);
        }
        if (gears.contains(GEAR_FIRECANNON)) {
            firecannonBolt(player, victim);
        }
        if (gears.contains(GEAR_STORMRAZOR)) {
            stormrazorBolt(player, victim);
        }
    }

    // ------------------------------------------------------------------ //
    // 岚切：盈能攻击电弧（100 闪电魔法伤害 + 45% 移速 1.5 秒）
    // ------------------------------------------------------------------ //

    private static void stormrazorBolt(ServerPlayer player, LivingEntity victim) {
        GearConfig.OnHitEffect effect = com.example.lolaccessories.config.GearConfigManager
                .get(GEAR_STORMRAZOR).findEffect("stormrazor_bolt").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        float damage = (float) (effect.base_damage > 0 ? effect.base_damage : 100.0D);
        IronsSpellDamage.apply(player, victim, damage, IronsSpellDamage.resolve(effect.school));
        // 45% 移速 1.5 秒
        int dur = (int) Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 1.5D) * 20.0D);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 2, true, false));
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.05D);
        }
        player.level().playSound(null, victim.blockPosition(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    // ------------------------------------------------------------------ //
    // 斯塔缇克电刃：闪电链
    // ------------------------------------------------------------------ //

    private static void statikkChain(ServerPlayer player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        GearConfig.OnHitEffect effect = com.example.lolaccessories.config.GearConfigManager
                .get(GEAR_STATIKK).findEffect("statikk_chain").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        float damage = (float) (effect.base_damage > 0 ? effect.base_damage : 70.0D);
        int jumps = effect.amount > 0 ? (int) effect.amount : 6;
        var school = IronsSpellDamage.resolve(effect.school);

        List<LivingEntity> hit = new ArrayList<>();
        LivingEntity current = target;
        Vec3 prevPos = null;
        for (int i = 0; i <= jumps; i++) {
            hit.add(current);
            IronsSpellDamage.apply(player, current, damage, school);
            // 粒子闪电链：首目标自天而降，后续各跳为电弧传导
            if (prevPos == null) {
                spawnStrikeParticles(level, current);
            } else {
                arcParticles(level, prevPos, current.position());
            }
            // 找下一个最近的未命中敌人
            LivingEntity next = null;
            double best = 8.0D * 8.0D;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    current.getBoundingBox().inflate(8.0D),
                    e -> e != player && e.isAlive() && !hit.contains(e)
                            && LolNewEpicPassiveEvents.isEnemyOf(player, e))) {
                double d = e.distanceToSqr(current);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            prevPos = current.position();
            current = next;
        }
        level.playSound(null, target.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4F, 1.6F);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    /** 粒子电击：从目标头顶上方 5 格劈下电火花柱（替代原版闪电实体，不着火）。 */
    private static void spawnStrikeParticles(ServerLevel level, LivingEntity target) {
        Vec3 top = new Vec3(
                target.getX() + (level.random.nextDouble() - 0.5D) * 0.6D,
                target.getEyeY() + 5.0D,
                target.getZ() + (level.random.nextDouble() - 0.5D) * 0.6D);
        arcParticles(level, top, new Vec3(target.getX(), target.getY(), target.getZ()));
    }

    /** 两点间用电火花（辅以光尘）采样出一条轻微抖动的电弧。 */
    private static void arcParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int samples = Math.max(4, (int) Math.floor(d.length() / 0.4D));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 p = from.add(d.scale(t)).add(
                    (level.random.nextDouble() - 0.5D) * 0.3D,
                    (level.random.nextDouble() - 0.5D) * 0.2D,
                    (level.random.nextDouble() - 0.5D) * 0.3D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 疾射火炮：盈能攻击附加火焰魔法伤害
    // ------------------------------------------------------------------ //

    private static void firecannonBolt(ServerPlayer player, LivingEntity victim) {
        GearConfig.OnHitEffect effect = com.example.lolaccessories.config.GearConfigManager
                .get(GEAR_FIRECANNON).findEffect("firecannon_bolt").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        float damage = (float) (effect.base_damage > 0 ? effect.base_damage : 120.0D);
        IronsSpellDamage.apply(player, victim, damage, IronsSpellDamage.resolve(effect.school));
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }
}
