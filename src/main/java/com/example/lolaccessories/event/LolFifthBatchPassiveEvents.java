package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第十五批传说装备被动：死亡之舞 / 焚天 / 兰德里的折磨 / 挺进破坏者（主动技本体）。
 * 炼金朋克链锯剑（劈削）完全复用现有 grievous_wounds_physical，无需在此处理。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolFifthBatchPassiveEvents {

    public static final String GEAR_DEATHS_DANCE = "deathsdance";
    public static final String GEAR_SUNDERED_SKY = "sundered_sky";
    public static final String GEAR_LIANDRYS = "liandrys_torment";
    public static final String GEAR_STRIDEBREAKER = "stridebreaker";

    private static final Map<UUID, BleedState> BLEEDS = new HashMap<>();
    private static final Map<UUID, Object[]> RECENT_HURT = new HashMap<>();
    private static final Map<UUID, double[]> DEFIY_HEALS = new HashMap<>();
    private static final Map<UUID, Long> LIGHTSHIELD_CD = new HashMap<>();
    private static final Map<UUID, SufferState> SUFFER = new HashMap<>();
    private static final Map<UUID, double[]> SHOCKWAVE_MS = new HashMap<>();
    private static final UUID SHOCKWAVE_MS_UUID =
            UUID.fromString("a1b2c3d4-3335-4a5b-8c6d-000000000035");

    private LolFifthBatchPassiveEvents() {
    }

    // ------------------------------------------------------------------ //
    // 攻击侧：蔑视伤害记录 / 兰德里折磨与受苦 / 焚天光盾打击
    // ------------------------------------------------------------------ //

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurtAttacker(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        long now = System.currentTimeMillis();
        boolean wearingDD = CuriosGearWear.isWearing(attacker, GEAR_DEATHS_DANCE);
        boolean wearingLiandrys = CuriosGearWear.isWearing(attacker, GEAR_LIANDRYS);
        if (!wearingDD && !wearingLiandrys
                && !CuriosGearWear.isWearing(attacker, GEAR_SUNDERED_SKY)) {
            return;
        }
        if (!LolNewEpicPassiveEvents.isEnemyOf(attacker, victim)) {
            return;
        }
        // 装备效果不对友善或被动生物生效
        if (LolNewEpicPassiveEvents.isPassiveFriendly(victim)) {
            return;
        }
        // 死亡之舞·蔑视：记录「我 3 秒内伤害过它」
        if (wearingDD) {
            RECENT_HURT.put(victim.getUUID(), new Object[]{attacker.getUUID(), now});
        }
        // 兰德里·折磨：技能（魔法）伤害命中 → 灼烧（每 0.5 秒 1% 最大生命，3 秒）
        if (wearingLiandrys && isMagicDamage(source)) {
            GearConfig config = GearConfigManager.get(GEAR_LIANDRYS);
            GearConfig.OnHitEffect torment = config == null
                    ? null : config.findEffect("torment").orElse(null);
            if (torment != null && torment.enabled) {
                double dps = victim.getMaxHealth()
                        * (torment.amount > 0 ? torment.amount * 2.0D : 0.02D);
                LolNewEpicPassiveEvents.startBurn(victim, dps,
                        torment.duration_seconds > 0 ? torment.duration_seconds : 3.0D,
                        torment.school);
            }
            // 受苦：造成伤害保持战斗 + 当前层数增伤（每层 +2%，至多 3 层）
            SufferState state = SUFFER.computeIfAbsent(attacker.getUUID(), k -> new SufferState());
            state.lastCombatMs = now;
            if (state.stacks > 0) {
                event.setAmount(event.getAmount() * (float) (1.0D + state.stacks * 0.02D));
            }
        }
        // 焚天·光盾打击：普攻命中（每目标 10 秒）→ 强制暴击 + 治疗
        if (CuriosGearWear.isWearing(attacker, GEAR_SUNDERED_SKY)
                && isBasicAttack(source, attacker)) {
            Long next = LIGHTSHIELD_CD.get(victim.getUUID());
            if (next == null || now >= next) {
                GearConfig config = GearConfigManager.get(GEAR_SUNDERED_SKY);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("lightshield_strike").orElse(null);
                if (effect != null && effect.enabled) {
                    long cd = Math.round((effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 10.0D) * 1000.0D);
                    LIGHTSHIELD_CD.put(victim.getUUID(), now + cd);
                    com.example.lolaccessories.combat.LolCritSystem.forceCrit(victim, now + 100L);
                    double adRatio = isRangedWeapon(attacker)
                            ? (effect.bonus_pct > 0 ? effect.bonus_pct : 0.45D)
                            : (effect.amount > 0 ? effect.amount : 0.9D);
                    double heal = adRatio * attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            + (effect.max_health_pct > 0 ? effect.max_health_pct : 0.04D)
                            * Math.max(0.0D, attacker.getMaxHealth() - attacker.getHealth());
                    attacker.heal((float) heal);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 受害侧：死亡之舞·无视痛苦
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurtVictim(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, GEAR_DEATHS_DANCE)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.typeHolder().unwrapKey()
                .map(key -> key.location().getPath().equals("out_of_world"))
                .orElse(false)) {
            return; // 虚空/真实伤害不存储
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        GearConfig config = GearConfigManager.get(GEAR_DEATHS_DANCE);
        GearConfig.OnHitEffect effect = config == null
                ? null : config.findEffect("ignore_pain").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        double rate = effect.amount > 0 ? effect.amount : 0.30D;
        long durationMs = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D) * 1000.0D);
        long now = System.currentTimeMillis();
        BleedState state = BLEEDS.computeIfAbsent(player.getUUID(), k -> new BleedState());
        state.stored += amount * rate;
        state.expireMs = now + durationMs;
        if (state.nextTickMs <= 0) {
            state.nextTickMs = now + 1000L;
        }
        event.setAmount(amount * (float) (1.0D - rate));
    }

    /** 蔑视：3 秒内被我伤害过的目标阵亡 → 净化流血 + 2 秒持续回血（75% 额外攻击力）。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Object[] record = RECENT_HURT.remove(event.getEntity().getUUID());
        if (record == null || System.currentTimeMillis() - (long) record[1] > 3000L) {
            return;
        }
        UUID wearerId = (UUID) record[0];
        BLEEDS.remove(wearerId);
        MinecraftServer server = event.getEntity().level().getServer();
        if (server == null) {
            return;
        }
        ServerPlayer wearer = server.getPlayerList().getPlayer(wearerId);
        if (wearer == null || !wearer.isAlive() || !CuriosGearWear.isWearing(wearer, GEAR_DEATHS_DANCE)) {
            return;
        }
        GearConfig config = GearConfigManager.get(GEAR_DEATHS_DANCE);
        GearConfig.OnHitEffect defy = config == null ? null : config.findEffect("defy").orElse(null);
        if (defy == null || !defy.enabled) {
            return;
        }
        double pct = defy.bonus_pct > 0 ? defy.bonus_pct : 0.75D;
        AttributeInstance ad = wearer.getAttribute(Attributes.ATTACK_DAMAGE);
        double extraAd = ad == null ? 0.0D : Math.max(0.0D, ad.getValue() - ad.getBaseValue());
        double perTick = extraAd * pct / 8.0D;
        DEFIY_HEALS.put(wearerId, new double[]{perTick, 8, System.currentTimeMillis() + 250L});
    }

    // ------------------------------------------------------------------ //
    // 周期维护：流血扣血 / 蔑视回血 / 受苦叠层 / 冲击波移速衰减
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        // 流血：每 1 秒扣剩余存量的 1/3（真实伤害语义）
        Iterator<Map.Entry<UUID, BleedState>> it = BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BleedState> entry = it.next();
            BleedState state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || now >= state.expireMs
                    || !CuriosGearWear.isWearing(player, GEAR_DEATHS_DANCE)) {
                it.remove();
                continue;
            }
            if (state.stored > 0 && now >= state.nextTickMs) {
                state.nextTickMs += 1000L;
                float tick = state.stored / 3.0F;
                state.stored = Math.max(0.0F, state.stored - tick);
                player.hurt(player.damageSources().fellOutOfWorld(), tick);
            }
        }
        // 蔑视回血（每 0.25 秒一次，共 8 次）
        Iterator<Map.Entry<UUID, double[]>> heals = DEFIY_HEALS.entrySet().iterator();
        while (heals.hasNext()) {
            Map.Entry<UUID, double[]> entry = heals.next();
            double[] plan = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || plan[1] <= 0) {
                heals.remove();
                continue;
            }
            if (now >= plan[2]) {
                plan[2] = now + 250L;
                plan[1] -= 1;
                player.heal((float) plan[0]);
                if (plan[1] <= 0) {
                    heals.remove();
                }
            }
        }
        // 兰德里·受苦叠层（战斗中每 1 秒 1 层，至多 3 层；脱战 3 秒清零）
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!CuriosGearWear.isWearing(player, GEAR_LIANDRYS)) {
                SUFFER.remove(player.getUUID());
                continue;
            }
            GearConfig config = GearConfigManager.get(GEAR_LIANDRYS);
            GearConfig.OnHitEffect effect = config == null
                    ? null : config.findEffect("suffering").orElse(null);
            if (effect == null || !effect.enabled) {
                SUFFER.remove(player.getUUID());
                continue;
            }
            SufferState state = SUFFER.computeIfAbsent(player.getUUID(), k -> new SufferState());
            int max = Math.max(1, effect.max_stacks > 0 ? effect.max_stacks : 3);
            long window = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D) * 1000.0D);
            if (now - state.lastCombatMs > window) {
                state.stacks = 0;
            } else if (state.stacks < max
                    && now - state.lastStackMs >= Math.round(
                    (effect.interval_seconds > 0 ? effect.interval_seconds : 1.0D) * 1000.0D)) {
                state.stacks++;
                state.lastStackMs = now;
            }
        }
        // 冲击波移速衰减
        Iterator<Map.Entry<UUID, double[]>> ms = SHOCKWAVE_MS.entrySet().iterator();
        while (ms.hasNext()) {
            Map.Entry<UUID, double[]> entry = ms.next();
            double[] plan = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || now >= (long) plan[1] || plan[0] <= 0.01D) {
                if (player != null) {
                    applyShockwaveSpeed(player, 0.0D);
                }
                ms.remove();
                continue;
            }
            plan[0] *= 0.78D;
            applyShockwaveSpeed(player, plan[0]);
        }
        // 记录表防泄漏
        if (RECENT_HURT.size() > 256) {
            List<UUID> stale = new ArrayList<>();
            for (Map.Entry<UUID, Object[]> e : RECENT_HURT.entrySet()) {
                if (now - (long) e.getValue()[1] > 3000L) {
                    stale.add(e.getKey());
                }
            }
            stale.forEach(RECENT_HURT::remove);
        }
        if (LIGHTSHIELD_CD.size() > 256) {
            LIGHTSHIELD_CD.entrySet().removeIf(e -> now >= e.getValue());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        BLEEDS.remove(event.getEntity().getUUID());
        RECENT_HURT.remove(event.getEntity().getUUID());
        DEFIY_HEALS.remove(event.getEntity().getUUID());
        SUFFER.remove(event.getEntity().getUUID());
        SHOCKWAVE_MS.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ //
    // 挺进破坏者·破阵冲击波（主动技本体，由 LolNewActiveSkillEvents 调用）
    // ------------------------------------------------------------------ //

    /**
     * 破阵冲击波：对周围 4.5 格敌方造成 80% 攻击力物理伤害 + 减速 3 秒，
     * 每命中一个敌方目标获得持续衰减的 35% 移速（3 秒）。返回命中数。
     */
    public static int shockwave(ServerPlayer player) {
        GearConfig config = GearConfigManager.get(GEAR_STRIDEBREAKER);
        GearConfig.OnHitEffect effect = config == null
                ? null : config.findEffect("shockwave").orElse(null);
        if (effect == null || !effect.enabled) {
            return 0;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 4.5D;
        double adRatio = effect.power_ratio > 0 ? effect.power_ratio : 0.8D;
        int durTicks = (int) Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D) * 20.0D);
        float damage = (float) (adRatio * player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        var physical = player.damageSources().playerAttack(player);
        int hits = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && LolNewEpicPassiveEvents.isEnemyOf(player, e))) {
            target.hurt(physical, damage);
            int amp = Math.max(0, (int) Math.round(
                    (effect.amount > 0 ? effect.amount : 0.35D) / 0.15D) - 1);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durTicks, amp, true, false));
            hits++;
        }
        if (hits > 0) {
            long now = System.currentTimeMillis();
            SHOCKWAVE_MS.put(player.getUUID(), new double[]{0.35D, now + durTicks * 50L});
            applyShockwaveSpeed(player, 0.35D);
        }
        return hits;
    }

    private static void applyShockwaveSpeed(ServerPlayer player, double value) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        if (attr.getModifier(SHOCKWAVE_MS_UUID) != null) {
            attr.removeModifier(SHOCKWAVE_MS_UUID);
        }
        if (value > 0.01D) {
            attr.addTransientModifier(new AttributeModifier(SHOCKWAVE_MS_UUID,
                    "stridebreaker_shockwave_speed", value, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ------------------------------------------------------------------ //
    // 工具
    // ------------------------------------------------------------------ //

    /** 魔法伤害：铁魔法学派 + 原版魔法/间接魔法。 */
    private static boolean isMagicDamage(DamageSource source) {
        boolean irons = source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks"))
                .orElse(false);
        return irons || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    /** 普攻：玩家本人的近战直击或其归属的物理弹射物（非魔法）。 */
    private static boolean isBasicAttack(DamageSource source, ServerPlayer attacker) {
        if (IronsCompatHelper.isIronSpellDamage(source)) {
            return false;
        }
        if (source.getDirectEntity() == attacker) {
            return true;
        }
        return source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() != null
                && projectile.getOwner().getUUID().equals(attacker.getUUID());
    }

    /** 远程判定：主手为弹射物武器（弓/弩）视为远程。 */
    private static boolean isRangedWeapon(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
    }

    /** 轻量铁魔法伤害判定（避免依赖 LolNewEpicPassiveEvents 的私有工具）。 */
    private static final class IronsCompatHelper {
        static boolean isIronSpellDamage(DamageSource source) {
            return source.typeHolder().unwrapKey()
                    .map(key -> key.location().getNamespace().equals("irons_spellbooks"))
                    .orElse(false);
        }
    }

    private static final class BleedState {
        float stored;
        long expireMs;
        long nextTickMs;
    }

    private static final class SufferState {
        int stacks;
        long lastStackMs;
        long lastCombatMs;
    }
}
