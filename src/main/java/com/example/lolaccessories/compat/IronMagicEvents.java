package com.example.lolaccessories.compat;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 铁魔法联动事件：卢登的回声——「回声」被动。
 *
 * <p>玩家佩戴卢登的回声造成铁魔法法术伤害时触发（有独立冷却）：从命中目标身上炸出
 * {@code echo_count} 道紫色回声光球；每拥有 100% 末影法术强度额外多 1 道。</p>
 *
 * <p>时序：法术自身的伤害照常先结算（由铁魔法完成），随后回声光球以潜影贝式曲线索敌——
 * 主目标分到 1 道全额回声，其周围 {@code radius_blocks} 格内的敌对生物各分 1 道全额
 * （伤害 = {@code base_damage + power_ratio × 末影法术强度}，纯魔法伤害）；敌对生物不足
 * “总弹道数 − 1”时，多余光球折返主目标，每道只造成全额伤害的 {@code bonus_pct}。
 * 伤害在光球真正命中的当刻结算。</p>
 *
 * <p>所有数值来自 ludens_echo.json。光球是注册的实体（紫色发光球渲染 + 服务端追踪），
 * 不使用逐刻粒子模拟，表现力接近铁魔法自带的法术弹射物。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IronMagicEvents {

    /** 每拥有 100% 末影法术强度，就额外多发射 1 道回声。 */
    private static final double EXTRA_PER_POWER_PERCENT = 100.0D;

    /**
     * 回声独立冷却：记录每个玩家上次触发的系统毫秒时间戳。
     *
     * <p>不能用“世界游戏刻”做这个记录：切换维度 / 创建新存档后，新世界的游戏时间会比
     * 旧世界留下的记录值更小，导致 {@code now - last < cooldown} 恒成立，冷却永不过期，
     * 被动在新存档里会彻底失效。系统毫秒单调递增、跨存档稳定。</p>
     */
    private static final Map<UUID, Long> LAST_ECHO_MS = new HashMap<>();

    private IronMagicEvents() {
    }

    @SubscribeEvent
    public static void onIronSpellDamage(LivingHurtEvent event) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !victim.isAlive()) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null || !IronsCompat.isIronSpellDamage(source)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player) || victim == player) {
            return;
        }

        // 只有佩戴卢登的回声（手饰）时才会触发
        if (!CuriosGearWear.isWearing(player, "ludens_echo")) {
            return;
        }

        GearConfig config = GearConfigManager.get("ludens_echo");
        GearConfig.OnHitEffect echo = config.findEffect("echo").orElse(null);
        if (echo == null || !echo.enabled) {
            return;
        }

        // 独立冷却：12 秒基础冷却受铁魔法「冷却缩减」属性减免（默认 1.0 = 无缩减，每 +0.1 即 10%）
        long nowMs = System.currentTimeMillis();
        double cooldownReduction = Math.max(0.0D, Math.min(0.8D,
                readMagicBonus(player, IronsCompat.COOLDOWN_REDUCTION)));
        // 装备技能急速独立乘区（LoL 公式：CDR = 急速/(100+急速)）
        long cooldownMs = Math.max(1L,
                Math.round(echo.cooldown_seconds * 1000.0D * (1.0D - cooldownReduction)
                        * com.example.lolaccessories.util.HasteMath.gearHasteFactor(player)));
        Long lastMs = LAST_ECHO_MS.get(player.getUUID());
        if (lastMs != null && nowMs - lastMs < cooldownMs) {
            return;
        }
        LAST_ECHO_MS.put(player.getUUID(), nowMs);
        if (LAST_ECHO_MS.size() > 128) {
            LAST_ECHO_MS.entrySet().removeIf(entry -> nowMs - entry.getValue() > 20L * 60L * 1000L);
        }

        // 末影法术强度：铁魔法百分比属性默认 1.0 = 无加成，净加成 +1.0 即 +100%
        double enderPowerPercent = readMagicBonus(player, IronsCompat.ENDER_SPELL_POWER) * 100.0D;
        LOLAccessories.LOGGER.info("[卢登回声] {} 的法术命中 {}，触发回声：末影法强加成 {:.1f}%，本次冷却 {}ms",
                player.getName().getString(), victim.getName().getString(), enderPowerPercent, cooldownMs);

        // 同步冷却给客户端做 HUD 可视化（回声确认触发后才发送；HUD 用游戏刻做进度条，需换算）
        if (player instanceof ServerPlayer serverPlayer) {
            long cooldownTicks = Math.max(1L, cooldownMs / 50L);
            LOLNetworking.sendEchoCooldown(serverPlayer, (int) cooldownTicks);
        }

        ServerLevel level = (ServerLevel) victim.level();
        // 触发点视觉反馈：主目标被命中的瞬间迸出紫色能量
        Vec3 fireAt = victim.position().add(0.0D, victim.getBbHeight() * 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL, fireAt.x, fireAt.y, fireAt.z, 12,
                0.3D, 0.3D, 0.3D, 0.05D);
        level.sendParticles(ParticleTypes.WITCH, fireAt.x, fireAt.y, fireAt.z, 6,
                0.2D, 0.2D, 0.2D, 0.0D);

        launchEchoVolley(level, player, victim, echo, enderPowerPercent);
    }

    /**
     * 发射一整轮回声光球：计算总弹道数 → 分配目标 → 逐道注册为追踪弹射物。
     *
     * <p>分配规则：主目标 1 道全额 → 周围敌对生物（按距离近优先）各 1 道全额 →
     * 不够分时剩余光球折返主目标（每道只造成 bonus_pct 伤害）。</p>
     */
    private static void launchEchoVolley(ServerLevel level, Player owner, LivingEntity victim,
                                         GearConfig.OnHitEffect echo, double enderPowerPercent) {
        // 总弹道数 = 基础弹道数 + 每 100% 末影法术强度多 1 道
        int baseCount = Math.max(1, (int) Math.round(echo.echo_count));
        int extraCount = (int) Math.floor(enderPowerPercent / EXTRA_PER_POWER_PERCENT);
        int total = Math.max(1, baseCount + extraCount);

        // 每道全额回声的伤害 = base_damage + power_ratio × 末影法术强度
        float echoDamage = (float) (echo.base_damage + echo.power_ratio * enderPowerPercent);
        if (echoDamage <= 0.0F) {
            return;
        }
        // 多余回声折返主目标时，每道只造成全额伤害的 bonus_pct
        float leftoverDamage = echoDamage * (float) Math.max(0.0D, Math.min(1.0D, echo.bonus_pct));

        // 索敌：主目标周围 radius_blocks 内的敌对生物，越近越优先
        List<LivingEntity> others = findOtherTargets(owner, victim, echo.radius_blocks);
        int othersCap = total - 1;
        int othersUsed = Math.min(others.size(), othersCap);
        int leftoverCount = othersCap - othersUsed;

        Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.35D, 0.0D);
        int index = 0;

        // 1. 主目标必定吃到 1 道全额回声
        spawnOrb(level, owner, victim, echoDamage, center, index++, total);

        // 2. 周围敌对生物各分 1 道全额回声（不足 total-1 只时按数量来）
        for (int i = 0; i < othersUsed; i++) {
            spawnOrb(level, owner, others.get(i), echoDamage, center, index++, total);
        }

        // 3. 敌对生物不够分时，多余回声折返主目标，仅造成 leftoverDamage
        for (int i = 0; i < leftoverCount; i++) {
            spawnOrb(level, owner, victim, leftoverDamage, center, index++, total);
        }

        LOLAccessories.LOGGER.info("[卢登回声] {} 发射 {} 道回声：主目标全额 {} 点，分给 {} 只邻近目标（全额 {} 点），折返 {} 道（每道 {} 点）",
                owner.getName().getString(), total, echoDamage,
                othersUsed, echoDamage, leftoverCount, leftoverDamage);
    }

    /**
     * 从主目标周围环上的一个出发点发射一道回声光球：起点位置随 index 均匀分布在
     * 一个半径略不同的环上，让多道光球看起来像从目标身上炸开，再各自曲线索敌。
     */
    private static void spawnOrb(ServerLevel level, Player owner, LivingEntity target,
                                 float damage, Vec3 center, int index, int total) {
        double angle = (index + 0.5D) * 2.0D * Math.PI / Math.max(1, total);
        double radius = 1.4D + (index % 3) * 0.45D;
        Vec3 outward = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 start = center.add(outward.scale(radius));

        EchoOrbEntity.launch(level, owner, target, damage, start, outward);
    }

    /**
     * 找出主目标周围 radius 格内、可被回声击中的敌对生物（不含主目标与佩戴者本身）。
     * 越靠近主目标越靠前，确保弹道优先命中近处的敌人。
     */
    private static List<LivingEntity> findOtherTargets(Player owner, LivingEntity victim, double radius) {
        List<LivingEntity> list = victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(Math.max(0.0D, radius)),
                entity -> entity != victim
                        && entity != owner
                        && entity.isAlive()
                        && isHostileTo(owner, entity));
        list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(victim)));
        return list;
    }

    /**
     * 判定某个生物是否是佩戴者的“敌对生物”。只有真正的敌人会吃到回声；
     * 友方、中立村民/动物等都不会被波及。
     */
    private static boolean isHostileTo(Player owner, LivingEntity entity) {
        if (entity instanceof Player other) {
            return other != owner && owner.canHarmPlayer(other);
        }
        if (owner.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof Monster) {
            return true;
        }
        if (entity instanceof NeutralMob neutral && neutral.isAngryAt(owner)) {
            return true;
        }
        // 当前正在攻击佩戴者的中立生物（如野狼）也算敌人
        return entity instanceof Mob mob && mob.getTarget() == owner;
    }

    /**
     * 读取铁魔法“百分比类”属性（默认值 1.0 = 无加成）相对默认值的净加成量：
     * 例如 cooldown_reduction 值 1.1 → +0.1（10% 冷却缩减），ender_spell_power 值 2.0 → +1.0（+100%）。
     */
    private static double readMagicBonus(LivingEntity entity, String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return 0.0D;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute) - attribute.getDefaultValue();
    }
}
