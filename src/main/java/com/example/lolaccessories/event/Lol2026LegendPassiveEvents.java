package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.init.ModMobEffects;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.GearFxBroadcast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2026 海克斯赛季 8 件传说装备（3 级）的服务端结算（本模组 2026-09-07 第三批）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>破垒者 bastionbreaker——成型炸药（shaped_charge）：近战普攻命中后对目标结算真实伤害；</li>
 *   <li>无穷饥渴 endless_hunger——饥馑（famine）/ 盛宴（feast）；</li>
 *   <li>海克斯镜片 C44 hexoptics_c44——高倍望远镜（long_shot）；</li>
 *   <li>班德尔音管 bandlepipes——嘹亮旋律（fanfare）；</li>
 *   <li>原生质护带 protoplasm_harness——救主灵刃（protoplasm）；</li>
 *   <li>实现器 actualizer——法力成真（realize，主动，冷却 180s 且不受冷却缩减影响）；</li>
 *   <li>黄昏黎明 dusk_and_dawn——咒刃（spellblade，由铁魔法施法装填 {@link #armDuskSpellblade}）；</li>
 *   <li>猎魔人弩箭 fiendhunter_bolts——开战弹幕（barrage，由铁魔法终极施法装填 {@link #armBarrage}）。</li>
 * </ul>
 *
 * <p>动态属性修正器（饥馑冷却缩减 / 盛宴全能吸血 / 实现器冷却缩减 / 原生质移速与韧性）走
 * {@link CuriosGearWear} 佩戴查询 + 每秒刷新，卸下装备或状态到期即摘除，并向客户端同步
 * 属性包（数值面板即时反映）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Lol2026LegendPassiveEvents {

    // ===================== gear / effect id 常量 =====================

    public static final String GEAR_DUSK_AND_DAWN = "dusk_and_dawn";
    public static final String GEAR_FIENDHUNTER_BOLTS = "fiendhunter_bolts";
    public static final String GEAR_ENDLESS_HUNGER = "endless_hunger";
    public static final String GEAR_BASTIONBREAKER = "bastionbreaker";
    public static final String GEAR_ACTUALIZER = "actualizer";
    public static final String GEAR_HEXOPTICS_C44 = "hexoptics_c44";
    public static final String GEAR_BANDLEPIPES = "bandlepipes";
    public static final String GEAR_PROTOPLASM_HARNESS = "protoplasm_harness";

    public static final String EFFECT_SPELLBLADE = "spellblade";
    public static final String EFFECT_BARRAGE = "barrage";
    public static final String EFFECT_FAMINE = "famine";
    public static final String EFFECT_FEAST = "feast";
    public static final String EFFECT_SHAPED_CHARGE = "shaped_charge";
    public static final String EFFECT_REALIZE = "realize";
    public static final String EFFECT_LONG_SHOT = "long_shot";
    public static final String EFFECT_FANFARE = "fanfare";
    public static final String EFFECT_PROTOPLASM = "protoplasm";

    /** 实现器·法力成真期间给铁魔法冷却缩减属性的瞬态加成：10.0 = +1000%（铁魔法 1.0 = 无加成基准）。 */
    public static final double REALIZE_CDR_BONUS = 10.0D;
    /** 实现器·法力成真期间给铁魔法法术吟唱（蓄力）时间缩减属性的瞬态加成：10.0 = +1000%。 */
    public static final double REALIZE_CAST_TIME_BONUS = 10.0D;

    /** 原生质救主灵刃触发的“伤到谁”归因窗口（秒），数值语义同 bandlepipes 设计。 */
    private static final double FANFARE_ATTRIBUTION_WINDOW_SEC = 5.0D;

    // ===================== 瞬态修正器 UUID =====================

    private static final UUID FAMINE_CDR_UUID = UUID.fromString("20260907-f000-4000-8000-000000000001");
    private static final UUID FEAST_OMNIVAMP_UUID = UUID.fromString("20260907-f000-4000-8000-000000000002");
    private static final UUID REALIZE_CDR_UUID = UUID.fromString("20260907-f000-4000-8000-000000000003");
    private static final UUID PROTOPLASM_MOVE_UUID = UUID.fromString("20260907-f000-4000-8000-000000000004");
    private static final UUID PROTOPLASM_TENACITY_UUID = UUID.fromString("20260907-f000-4000-8000-000000000005");
    private static final UUID REALIZE_CAST_TIME_UUID = UUID.fromString("20260907-f000-4000-8000-000000000006");

    // ===================== 运行时状态（玩家 UUID → …） =====================

    /** 成型炸药：下次允许触发的毫秒。 */
    private static final Map<UUID, Long> SHAPED_CHARGE_NEXT_MS = new HashMap<>();
    /** 盛宴：效果截止毫秒（佩戴者击杀/助攻后进入 8 秒全能吸血窗口）。 */
    private static final Map<UUID, Long> FEAST_UNTIL_MS = new HashMap<>();
    /** 原生质：下次允许触发的毫秒。 */
    private static final Map<UUID, Long> PROTOPLASM_NEXT_MS = new HashMap<>();
    /** 原生质：增益窗口截止毫秒（期间 +移速/韧性 + 分段治疗）。 */
    private static final Map<UUID, Long> PROTOPLASM_WINDOW_UNTIL_MS = new HashMap<>();
    /** 原生质：尚未分段发放的治疗量。 */
    private static final Map<UUID, Double> PROTOPLASM_HEAL_LEFT = new HashMap<>();
    /** 原生质：剩余发放次数。 */
    private static final Map<UUID, Integer> PROTOPLASM_HEAL_INSTALLMENTS = new HashMap<>();
    /** 咒刃：已装填（等待下一次普攻命中消耗）的截止毫秒。 */
    private static final Map<UUID, Long> DUSK_CHARGE_UNTIL_MS = new HashMap<>();
    /** 咒刃：装填锁——消耗后需等待内置冷却才能由再次施法装填。 */
    private static final Map<UUID, Long> DUSK_REARM_NEXT_MS = new HashMap<>();
    /** 开战弹幕：剩余“必定会心”的远程普攻次数。 */
    private static final Map<UUID, Integer> BARRAGE_CHARGES = new HashMap<>();
    /** 开战弹幕：剩余窗口截止毫秒。 */
    private static final Map<UUID, Long> BARRAGE_UNTIL_MS = new HashMap<>();
    /** 开战弹幕：下次允许装填的毫秒（装填内置冷却，默认 20 秒）。 */
    private static final Map<UUID, Long> BARRAGE_NEXT_MS = new HashMap<>();
    /** 实现器·法力成真：窗口截止毫秒。 */
    private static final Map<UUID, Long> REALIZE_UNTIL_MS = new HashMap<>();
    /** 嘹亮旋律：每位佩戴者下次允许触发的毫秒。 */
    private static final Map<UUID, Long> FANFARE_NEXT_MS = new HashMap<>();
    /** 最近伤害归因：目标实体 id →（造成伤害的玩家 uuid → 系统毫秒）。 */
    private static final Map<Integer, Map<UUID, Long>> RECENT_HURT = new HashMap<>();

    private static final int RECENT_HURT_MAX_AGE_MS = 6000;

    private Lol2026LegendPassiveEvents() {
    }

    // ===================== 外部联动入口 =====================

    /**
     * 铁魔法成功施法后装填黄昏黎明的咒刃（由 ISS 联动类调用）。
     * 处于咒刃内置冷却锁（施法过于频繁）时不会重复装填。
     */
    public static void armDuskSpellblade(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, GEAR_DUSK_AND_DAWN)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_DUSK_AND_DAWN, EFFECT_SPELLBLADE);
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long lockUntil = DUSK_REARM_NEXT_MS.get(uuid);
        if (lockUntil != null && now < lockUntil) {
            return;
        }
        double durationSec = effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D;
        DUSK_CHARGE_UNTIL_MS.put(uuid, now + Math.round(durationSec * 1000.0D));
    }

    /**
     * 铁魔法施放终极技能（基础法力消耗 &gt; 200）后装填猎魔人弩箭的开战弹幕
     * （由 ISS 联动类调用）：接下来 amount 次远程普攻必定会心，装填内置冷却 cooldown_seconds 秒。
     */
    public static void armBarrage(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, GEAR_FIENDHUNTER_BOLTS)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_FIENDHUNTER_BOLTS, EFFECT_BARRAGE);
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long next = BARRAGE_NEXT_MS.get(uuid);
        if (next != null && now < next) {
            return; // 装填冷却中
        }
        double cooldownSec = effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 20.0D;
        BARRAGE_NEXT_MS.put(uuid, now + Math.round(cooldownSec * 1000.0D));
        int charges = effect.amount > 0 ? (int) Math.round(effect.amount) : 3;
        BARRAGE_CHARGES.put(uuid, Math.max(1, charges));
        double durationSec = effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D;
        BARRAGE_UNTIL_MS.put(uuid, now + Math.round(durationSec * 1000.0D));
        GearFxBroadcast.window(player, FxKind.BARRAGE, (int) Math.round(durationSec * 20.0D));
    }

    /**
     * 实现器·法力成真激活（由主动技能事件在服务端调用）：开启 5 秒窗口，
     * 期间铁魔法施法法力双倍且冷却几乎立即可用。
     */
    public static void activateRealize(ServerPlayer player) {
        GearConfig.OnHitEffect effect = findEffect(GEAR_ACTUALIZER, EFFECT_REALIZE);
        if (effect == null || !effect.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        double durationSec = effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D;
        REALIZE_UNTIL_MS.put(player.getUUID(), now + Math.round(durationSec * 1000.0D));
        GearFxBroadcast.window(player, FxKind.REALIZE, (int) Math.round(durationSec * 20.0D));
        refreshRealize(player, now);
    }

    /** 实现器·法力成真窗口是否仍在生效（铁魔法施法结算层查询）。 */
    public static boolean isRealizeActive(Player player) {
        if (!CuriosGearWear.isWearing(player, GEAR_ACTUALIZER)) {
            return false;
        }
        Long until = REALIZE_UNTIL_MS.get(player.getUUID());
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * 开战弹幕结算（由暴击系统在“玩家射出的远程物理弹道命中”时调用）。
     * 有充能且命中有效目标时：该击必定按全额会心倍率结算，并附加原始伤害 crit_true_ratio 的真实伤害。
     * 返回 true 表示本击已被接管。
     */
    public static boolean tryFiendhunterBarrage(Player attacker, LivingHurtEvent event, double multiplier) {
        if (attacker.level().isClientSide) {
            return false;
        }
        LivingEntity victim = event.getEntity();
        if (victim == null || !isLegendaryTarget(attacker, victim)) {
            return false;
        }
        UUID uuid = attacker.getUUID();
        Integer charges = BARRAGE_CHARGES.get(uuid);
        Long until = BARRAGE_UNTIL_MS.get(uuid);
        if (charges == null || charges <= 0 || until == null
                || until <= System.currentTimeMillis()) {
            return false;
        }
        if (!CuriosGearWear.isWearing(attacker, GEAR_FIENDHUNTER_BOLTS)) {
            clearBarrage(uuid);
            return false;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_FIENDHUNTER_BOLTS, EFFECT_BARRAGE);
        if (effect == null || !effect.enabled) {
            return false;
        }

        int left = charges - 1;
        if (left <= 0) {
            BARRAGE_CHARGES.remove(uuid);
            BARRAGE_UNTIL_MS.remove(uuid);
            GearFxBroadcast.off(attacker, FxKind.BARRAGE);
        } else {
            BARRAGE_CHARGES.put(uuid, left);
        }

        // 弹幕命中：必定全额会心（原伤害 × 常规暴击倍率），不再掷自然暴击
        float base = event.getAmount();
        double crit = Math.max(1.0D, multiplier);
        event.setAmount((float) (base * crit));
        // 额外真实伤害：按命中前原始伤害的 crit_true_ratio
        double trueRatio = effect.crit_true_ratio > 0 ? effect.crit_true_ratio : 0.15D;
        if (trueRatio > 0.0D && base > 0.0F) {
            victim.hurt(victim.level().damageSources().fellOutOfWorld(), (float) (base * trueRatio));
        }
        if (attacker instanceof ServerPlayer serverPlayer) {
            recordRecentHurt(serverPlayer, victim);
        }
        spawnHitParticles(victim, false);
        return true;
    }

    /** 玩家在 RECENT_HURT 中的有效记录（是否最近伤害过该目标）。 */
    private static boolean hasRecentDamage(Player player, LivingEntity victim, double windowSec) {
        Map<UUID, Long> damagers = RECENT_HURT.get(victim.getId());
        if (damagers == null || damagers.isEmpty()) {
            return false;
        }
        Long ms = damagers.get(player.getUUID());
        return ms != null && System.currentTimeMillis() - ms <= Math.round(windowSec * 1000.0D);
    }

    // ===================== Forge 事件 =====================

    /** 命中结算：饥馑模式切换、成型炸药、咒刃、高倍望远镜与盛宴/嘹亮旋律的伤害归因。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) {
            return;
        }
        DamageSourceLike source = new DamageSourceLike(event);
        LivingEntity victim = event.getEntity();
        Player attacker = source.attacker();
        if (attacker == null || attacker == victim || !attacker.isAlive()) {
            return;
        }
        if (!(attacker instanceof ServerPlayer serverAttacker)) {
            return;
        }

        // 归因记录：盛宴（无尽饥渴）/ 嘹亮旋律（班德尔音管）需要“最近伤害过目标”
        if (isLegendaryTarget(serverAttacker, victim)
                && (CuriosGearWear.isWearing(serverAttacker, GEAR_ENDLESS_HUNGER)
                || CuriosGearWear.isWearing(serverAttacker, GEAR_BANDLEPIPES))) {
            recordRecentHurt(serverAttacker, victim);
        }

        boolean meleeBasic = source.meleeBasic();
        boolean rangedBasic = source.rangedBasic();
        if (!meleeBasic && !rangedBasic) {
            return;
        }

        if (!isLegendaryTarget(serverAttacker, victim)) {
            return;
        }

        if (meleeBasic) {
            handleShapedCharge(serverAttacker, victim);
        }
        if (CuriosGearWear.isWearing(serverAttacker, GEAR_DUSK_AND_DAWN)) {
            consumeDuskSpellblade(serverAttacker, victim);
        }
        if (rangedBasic) {
            handleLongShot(serverAttacker, victim, event);
        }
    }

    /** 原生质救主灵刃：本次伤害将把血量压到阈值以下时触发。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, GEAR_PROTOPLASM_HARNESS)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_PROTOPLASM_HARNESS, EFFECT_PROTOPLASM);
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long nextMs = PROTOPLASM_NEXT_MS.get(uuid);
        if (nextMs != null && now < nextMs) {
            return;
        }
        double maxHp = player.getMaxHealth();
        double threshold = maxHp * Math.max(0.0D, effect.trigger_health_percent);
        if (player.getHealth() - event.getAmount() > threshold) {
            return;
        }

        double fraction = levelFraction(player);
        double extraHealth = lerp(effect.health_min, effect.health_max, fraction);
        double healTotal = lerp(effect.heal_min, effect.heal_max, fraction);
        double durationSec = Math.max(1.0D, effect.duration_seconds);
        double cooldownSec = Math.max(1.0D, effect.cooldown_seconds);

        // 玩家已带着原版吸收效果（金苹果/图腾等外部黄心）时不叠加护盾，避免污染其结算
        if (player.hasEffect(MobEffects.ABSORPTION)) {
            return;
        }

        // 黄心护盾：以吸收值承载救主灵刃护盾（显示金色心，不挂药水效果，避免被清除/叠加异常）
        long shieldMs = Math.round(durationSec * 1000.0D);
        ShieldHpService.apply(player, ShieldHpService.SOURCE_PROTOPLASM,
                (float) extraHealth, shieldMs);
        GearFxBroadcast.window(player, FxKind.SHIELD_PROTOPLASM,
                (int) Math.round(durationSec * 20.0D));

        // 5 秒分段治疗（数值含治疗与护盾强度加成）
        double scaledHeal = healTotal * (1.0D + healPowerBonus(player));
        long until = now + Math.round(durationSec * 1000.0D);
        PROTOPLASM_WINDOW_UNTIL_MS.put(uuid, until);
        PROTOPLASM_HEAL_LEFT.put(uuid, scaledHeal);
        PROTOPLASM_HEAL_INSTALLMENTS.put(uuid, Math.max(1, (int) Math.round(durationSec)));
        PROTOPLASM_NEXT_MS.put(uuid, now + Math.round(cooldownSec * 1000.0D));

        refreshProtoplasm(player, now);
        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.HEART, center.x, center.y, center.z,
                    12, 0.5D, 0.4D, 0.5D, 0.05D);
        }
        LOLAccessories.LOGGER.info("[原生质救主] {} 触发救主灵刃：黄心护盾 {}，分段治疗 {}，冷却 {}s",
                player.getName().getString(), fmt(extraHealth), fmt(scaledHeal), fmt(cooldownSec));
    }

    /** 盛宴：近期被我伤害的目标死亡（击杀或助攻）时进入 8 秒全能吸血窗口。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Map<UUID, Long> damagers = RECENT_HURT.remove(victim.getId());
        if (damagers == null || damagers.isEmpty()) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_ENDLESS_HUNGER, EFFECT_FEAST);
        if (effect == null || !effect.enabled) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        double windowMs = (effect.damage_window_seconds > 0 ? effect.damage_window_seconds : 3.0D) * 1000.0D;
        double durationSec = effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D;
        for (Map.Entry<UUID, Long> entry : damagers.entrySet()) {
            if (now - entry.getValue() > windowMs) {
                continue;
            }
            ServerPlayer attacker = server.getPlayerList().getPlayer(entry.getKey());
            if (attacker == null || !attacker.isAlive() || attacker.isSpectator()) {
                continue;
            }
            if (!CuriosGearWear.isWearing(attacker, GEAR_ENDLESS_HUNGER)) {
                continue;
            }
            FEAST_UNTIL_MS.put(attacker.getUUID(), now + Math.round(durationSec * 1000.0D));
            refreshFeast(attacker, now);
        }
    }

    /** 嘹亮旋律：对（近期被我方佩戴者伤害的）目标施加负面药水效果后，触发友军增益。 */
    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity target = event.getEntity();
        if (instance == null || target == null || target.isDeadOrDying()
                || instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        Map<UUID, Long> damagers = RECENT_HURT.get(target.getId());
        if (damagers == null || damagers.isEmpty()) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_BANDLEPIPES, EFFECT_FANFARE);
        if (effect == null || !effect.enabled) {
            return;
        }
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        double attributionMs = FANFARE_ATTRIBUTION_WINDOW_SEC * 1000.0D;
        double cooldownMs = (effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 30.0D) * 1000.0D;
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 10.0D;
        int durationTicks = (int) Math.round(
                (effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D) * 20.0D);
        for (Map.Entry<UUID, Long> entry : damagers.entrySet()) {
            if (now - entry.getValue() > attributionMs) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !owner.isAlive() || owner.isSpectator()) {
                continue;
            }
            if (!CuriosGearWear.isWearing(owner, GEAR_BANDLEPIPES)) {
                continue;
            }
            UUID ownerId = owner.getUUID();
            Long nextFanfare = FANFARE_NEXT_MS.get(ownerId);
            if (nextFanfare != null && now < nextFanfare) {
                continue;
            }
            FANFARE_NEXT_MS.put(ownerId, now + Math.round(cooldownMs));
            int count = 0;
            for (ServerPlayer ally : LolLegendPassiveEvents.allyPlayersAround(owner, radius)) {
                if (!ally.isAlive() || ally.isSpectator()) {
                    continue;
                }
                ally.addEffect(new MobEffectInstance(ModMobEffects.FANFARE.get(),
                        durationTicks, 0, false, true));
                GearFxBroadcast.window(ally, FxKind.FANFARE, durationTicks);
                count++;
            }
        }
    }

    /** 每秒：刷新全部瞬态属性与分段治疗，并做过期清理。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            refreshFamine(player);
            refreshFeast(player, now);
            refreshRealize(player, now);
            refreshProtoplasm(player, now);
        }
        pruneTimers(now);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        SHAPED_CHARGE_NEXT_MS.remove(uuid);
        FEAST_UNTIL_MS.remove(uuid);
        PROTOPLASM_NEXT_MS.remove(uuid);
        PROTOPLASM_WINDOW_UNTIL_MS.remove(uuid);
        PROTOPLASM_HEAL_LEFT.remove(uuid);
        PROTOPLASM_HEAL_INSTALLMENTS.remove(uuid);
        DUSK_CHARGE_UNTIL_MS.remove(uuid);
        DUSK_REARM_NEXT_MS.remove(uuid);
        clearBarrage(uuid);
        BARRAGE_NEXT_MS.remove(uuid);
        REALIZE_UNTIL_MS.remove(uuid);
        FANFARE_NEXT_MS.remove(uuid);
    }

    // ===================== 命中效果 =====================

    /** 成型炸药：近战普攻命中后，结算 基础 50 + 1.5×护甲穿透 的真实伤害（20 秒冷却）。 */
    private static void handleShapedCharge(ServerPlayer attacker, LivingEntity victim) {
        if (!CuriosGearWear.isWearing(attacker, GEAR_BASTIONBREAKER)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_BASTIONBREAKER, EFFECT_SHAPED_CHARGE);
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = attacker.getUUID();
        long now = System.currentTimeMillis();
        Long nextMs = SHAPED_CHARGE_NEXT_MS.get(uuid);
        if (nextMs != null && now < nextMs) {
            return;
        }
        double cooldownSec = Math.max(1.0D, effect.cooldown_seconds);
        SHAPED_CHARGE_NEXT_MS.put(uuid, now + Math.round(cooldownSec * 1000.0D));

        double base = effect.base_damage > 0 ? effect.base_damage : 50.0D;
        double armorPierce = attacker.getAttributeValue(attributeOf("attributeslib:armor_pierce", null));
        double trueDamage = base + armorPierce
                * (effect.armor_pierce_scale > 0 ? effect.armor_pierce_scale : 1.5D);
        if (trueDamage > 0.0D) {
            victim.hurt(victim.level().damageSources().fellOutOfWorld(), (float) trueDamage);
            recordRecentHurt(attacker, victim);
        }
        spawnHitParticles(victim, true);
    }

    /** 黄昏黎明·咒刃：消耗装填，附加魔法伤害并治疗（数值来自 dusk_and_dawn.json）。 */
    private static void consumeDuskSpellblade(ServerPlayer attacker, LivingEntity victim) {
        UUID uuid = attacker.getUUID();
        Long chargeUntil = DUSK_CHARGE_UNTIL_MS.get(uuid);
        if (chargeUntil == null || chargeUntil <= System.currentTimeMillis()) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_DUSK_AND_DAWN, EFFECT_SPELLBLADE);
        if (effect == null || !effect.enabled) {
            return;
        }
        // 消耗本次装填，并进入内置冷却（期间施法不重新装填）
        DUSK_CHARGE_UNTIL_MS.remove(uuid);
        double cooldownSec = Math.max(0.0D, effect.cooldown_seconds);
        DUSK_REARM_NEXT_MS.put(uuid, System.currentTimeMillis() + Math.round(cooldownSec * 1000.0D));

        double ap = spellPowerOf(attacker);
        double ad = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double magicDamage = ad * effect.ad_ratio + ap * effect.ap_ratio;
        if (magicDamage > 0.0D) {
            // 咒刃等技能的魔法伤害 = 铁魔法学派伤害
            IronsSpellDamage.apply(attacker, victim, (float) magicDamage,
                    IronsSpellDamage.resolve(effect.school));
            recordRecentHurt(attacker, victim);
        }
        double maxHp = attacker.getMaxHealth();
        double heal = ap * effect.heal_ap_ratio + maxHp * effect.heal_hp_ratio;
        if (heal > 0.0D) {
            attacker.heal((float) (heal * (1.0D + healPowerBonus(attacker))));
        }
        spawnHitParticles(victim, true);
    }

    /** 高倍望远镜：远程普攻命中时，按命中距离（最多 distance_blocks 格）至多附加 amount 的增伤。 */
    private static void handleLongShot(ServerPlayer attacker, LivingEntity victim, LivingHurtEvent event) {
        if (!CuriosGearWear.isWearing(attacker, GEAR_HEXOPTICS_C44)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_HEXOPTICS_C44, EFFECT_LONG_SHOT);
        if (effect == null || !effect.enabled || effect.amount <= 0.0D) {
            return;
        }
        double distance = victim.distanceTo(attacker);
        double maxBlocks = Math.max(1.0D, effect.distance_blocks);
        double bonus = effect.amount * Math.min(1.0D, Math.max(0.0D, distance / maxBlocks));
        if (bonus <= 0.0D) {
            return;
        }
        float original = event.getAmount();
        float boosted = (float) (original * (1.0D + bonus));
        event.setAmount(boosted);
        spawnHitParticles(victim, false);
    }

    // ===================== 每秒动态属性刷新 =====================

    private static void refreshFamine(ServerPlayer player) {
        Attribute cdr = attributeOf(IronsCompat.COOLDOWN_REDUCTION, null);
        UUID uuid = player.getUUID();
        GearConfig.OnHitEffect effect = findEffect(GEAR_ENDLESS_HUNGER, EFFECT_FAMINE);
        if (cdr == null || effect == null || !effect.enabled
                || !CuriosGearWear.isWearing(player, GEAR_ENDLESS_HUNGER)) {
            removeTransient(player, cdr, FAMINE_CDR_UUID);
            return;
        }
        // 饥馑（英雄联盟近战口径）：不区分近战/远程，一律按 melee_ratio 折算
        double ratio = effect.melee_ratio > 0 ? effect.melee_ratio : 0.13D;
        double ad = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // 饥馑 = amount(0.05) + ratio × 攻击力 ÷100（点数换算成比例）
        double attrBonus = effect.amount + ratio * ad / 100.0D;
        setTransient(player, cdr, FAMINE_CDR_UUID, attrBonus, AttributeModifier.Operation.ADDITION);
    }

    private static void refreshFeast(ServerPlayer player, long now) {
        // 盛宴（英雄联盟官方口径）：短暂获得的是「全能吸血」，对所有类型伤害回血，而非生命偷取
        Attribute omnivamp = ModAttributes.LOL_OMNIVAMP.get();
        UUID uuid = player.getUUID();
        GearConfig.OnHitEffect effect = findEffect(GEAR_ENDLESS_HUNGER, EFFECT_FEAST);
        boolean active = effect != null && effect.enabled
                && CuriosGearWear.isWearing(player, GEAR_ENDLESS_HUNGER)
                && FEAST_UNTIL_MS.getOrDefault(uuid, 0L) > now;
        if (!active) {
            removeTransient(player, omnivamp, FEAST_OMNIVAMP_UUID);
            FEAST_UNTIL_MS.remove(uuid);
            return;
        }
        double bonus = effect.omnivamp_ratio > 0 ? effect.omnivamp_ratio : 0.15D;
        setTransient(player, omnivamp, FEAST_OMNIVAMP_UUID, bonus, AttributeModifier.Operation.ADDITION);
    }

    private static void refreshRealize(ServerPlayer player, long now) {
        Attribute cdr = attributeOf(IronsCompat.COOLDOWN_REDUCTION, null);
        Attribute castTime = attributeOf(IronsCompat.CAST_TIME_REDUCTION, null);
        UUID uuid = player.getUUID();
        boolean active = CuriosGearWear.isWearing(player, GEAR_ACTUALIZER)
                && REALIZE_UNTIL_MS.getOrDefault(uuid, 0L) > now;
        if (!active) {
            removeTransient(player, cdr, REALIZE_CDR_UUID);
            removeTransient(player, castTime, REALIZE_CAST_TIME_UUID);
            REALIZE_UNTIL_MS.remove(uuid);
            return;
        }
        if (cdr != null) {
            // +1000% 冷却缩减：10.0
            setTransient(player, cdr, REALIZE_CDR_UUID, REALIZE_CDR_BONUS,
                    AttributeModifier.Operation.ADDITION);
        }
        if (castTime != null) {
            // +1000% 法术吟唱（蓄力）时间缩减：10.0
            setTransient(player, castTime, REALIZE_CAST_TIME_UUID, REALIZE_CAST_TIME_BONUS,
                    AttributeModifier.Operation.ADDITION);
        }
    }

    private static void refreshProtoplasm(ServerPlayer player, long now) {
        UUID uuid = player.getUUID();
        GearConfig.OnHitEffect effect = findEffect(GEAR_PROTOPLASM_HARNESS, EFFECT_PROTOPLASM);
        long windowUntil = PROTOPLASM_WINDOW_UNTIL_MS.getOrDefault(uuid, 0L);
        boolean active = effect != null && effect.enabled
                && CuriosGearWear.isWearing(player, GEAR_PROTOPLASM_HARNESS)
                && windowUntil > now;
        Attribute moveSpeed = Attributes.MOVEMENT_SPEED;
        Attribute tenacity = ModAttributes.LOL_TENACITY.get();
        if (!active) {
            removeTransient(player, moveSpeed, PROTOPLASM_MOVE_UUID);
            removeTransient(player, tenacity, PROTOPLASM_TENACITY_UUID);
            PROTOPLASM_WINDOW_UNTIL_MS.remove(uuid);
            Double leftover = PROTOPLASM_HEAL_LEFT.remove(uuid);
            PROTOPLASM_HEAL_INSTALLMENTS.remove(uuid);
            if (leftover != null && leftover > 0.0D && !player.isDeadOrDying()) {
                player.heal((float) Math.min(leftover, player.getMaxHealth() * 2.0D));
            }
            return;
        }
        double moveRatio = effect.move_speed_ratio > 0 ? effect.move_speed_ratio : 0.10D;
        double tenacityRatio = effect.tenacity_ratio;
        setTransient(player, moveSpeed, PROTOPLASM_MOVE_UUID, moveRatio,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (tenacityRatio > 0.0D) {
            setTransient(player, tenacity, PROTOPLASM_TENACITY_UUID, tenacityRatio,
                    AttributeModifier.Operation.ADDITION);
        } else {
            removeTransient(player, tenacity, PROTOPLASM_TENACITY_UUID);
        }
        // 分段发放治疗（每秒一份）
        Integer installments = PROTOPLASM_HEAL_INSTALLMENTS.get(uuid);
        Double left = PROTOPLASM_HEAL_LEFT.get(uuid);
        if (installments != null && installments > 0 && left != null && left > 0.0D) {
            double chunk = left / installments;
            player.heal((float) chunk);
            PROTOPLASM_HEAL_LEFT.put(uuid, left - chunk);
            PROTOPLASM_HEAL_INSTALLMENTS.put(uuid, installments - 1);
        }
    }

    // ===================== 辅助 =====================

    private static void clearBarrage(UUID uuid) {
        BARRAGE_CHARGES.remove(uuid);
        BARRAGE_UNTIL_MS.remove(uuid);
    }

    /** 记录某玩家对某目标“刚刚造成伤害”（用于盛宴击杀归因 / 嘹亮旋律触发归因）。 */
    private static void recordRecentHurt(ServerPlayer attacker, LivingEntity victim) {
        long now = System.currentTimeMillis();
        RECENT_HURT.computeIfAbsent(victim.getId(), k -> new HashMap<>())
                .put(attacker.getUUID(), now);
    }

    /** 每秒清理过期的计时状态，避免内存累积。 */
    private static void pruneTimers(long now) {
        if (!RECENT_HURT.isEmpty()) {
            Iterator<Map.Entry<Integer, Map<UUID, Long>>> it = RECENT_HURT.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Map<UUID, Long>> entry = it.next();
                entry.getValue().entrySet()
                        .removeIf(e -> now - e.getValue() > RECENT_HURT_MAX_AGE_MS);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        SHAPED_CHARGE_NEXT_MS.entrySet().removeIf(e -> now - e.getValue() > 30_000L);
        DUSK_CHARGE_UNTIL_MS.entrySet().removeIf(e -> e.getValue() <= now);
        DUSK_REARM_NEXT_MS.entrySet().removeIf(e -> e.getValue() <= now);
        BARRAGE_UNTIL_MS.entrySet().removeIf(e -> e.getValue() <= now);
        BARRAGE_NEXT_MS.entrySet().removeIf(e -> e.getValue() <= now);
        FANFARE_NEXT_MS.entrySet().removeIf(e -> e.getValue() <= now);
        PROTOPLASM_NEXT_MS.entrySet().removeIf(e -> e.getValue() <= now);
        REALIZE_UNTIL_MS.entrySet().removeIf(e -> e.getValue() <= now);
        FEAST_UNTIL_MS.entrySet().removeIf(e -> e.getValue() <= now);
    }

    private static GearConfig.OnHitEffect findEffect(String gearId, String effectId) {
        GearConfig config = GearConfigManager.get(gearId);
        if (config == null) {
            return null;
        }
        return config.findEffect(effectId).orElse(null);
    }

    private static boolean isLegendaryTarget(Player owner, LivingEntity target) {
        return LolLegendPassiveEvents.isLegendaryTarget(owner, target);
    }

    private static void spawnHitParticles(LivingEntity victim, boolean magic) {
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 p = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        serverLevel.sendParticles(magic ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT,
                p.x, p.y, p.z, magic ? 10 : 12, 0.3D, 0.3D, 0.3D, 0.12D);
    }

    /** 治疗与护盾强度（倍率，净加成）。 */
    private static double healPowerBonus(LivingEntity entity) {
        return Math.max(0.0D, entity.getAttributeValue(ModAttributes.LOL_HEAL_POWER.get()));
    }

    /** 通用法术强度换算：铁魔法属性原值 ×100（未装铁魔法时按 1.0 = 100 法强）。 */
    private static double spellPowerOf(Player player) {
        Attribute attribute = attributeOf(IronsCompat.SPELL_POWER, null);
        if (attribute == null) {
            return 1.0D * 100.0D;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 100.0D : Math.max(0.0D, instance.getValue()) * 100.0D;
    }

    /** 原生质生命/治疗“按等级”插值：经验等级 0→下限、≥18→上限（镜像 LoL 1-18 级）。 */
    private static double levelFraction(Player player) {
        return Math.min(1.0D, Math.max(0.0D, player.experienceLevel / 18.0D));
    }

    private static double lerp(double min, double max, double fraction) {
        return min + (max - min) * Math.max(0.0D, Math.min(1.0D, fraction));
    }

    private static Attribute attributeOf(String id, Attribute fallback) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return fallback;
        }
        return BuiltInRegistries.ATTRIBUTE.get(location);
    }

    private static void setTransient(ServerPlayer player, Attribute attribute, UUID modifierId,
                                     double amount, AttributeModifier.Operation operation) {
        if (attribute == null || player == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(modifierId);
        if (current != null) {
            if (Math.abs(current.getAmount() - amount) < 1.0e-9
                    && current.getOperation() == operation) {
                return;
            }
            instance.removeModifier(modifierId);
        }
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(modifierId,
                    "lolaccessories.legend2026", amount, operation));
        }
        syncAttribute(player, instance);
    }

    private static void removeTransient(ServerPlayer player, Attribute attribute, UUID modifierId) {
        if (attribute == null || player == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.getModifier(modifierId) == null) {
            return;
        }
        instance.removeModifier(modifierId);
        syncAttribute(player, instance);
    }

    private static void syncAttribute(ServerPlayer player, AttributeInstance instance) {
        // 1.20.1 该构造接收 Collection<AttributeInstance>，由包内部打包为快照
        player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(),
                List.of(instance)));
    }

    /** 本次伤害源的基础信息快照，供命中判定复用。 */
    private record DamageSourceLike(LivingHurtEvent event) {

        net.minecraft.world.damagesource.DamageSource source() {
            return event.getSource();
        }

        Player attacker() {
            if (source().getEntity() instanceof Player player) {
                return player;
            }
            return null;
        }

        boolean magic() {
            return IronsCompat.isIronSpellDamage(source())
                    || source().getDirectEntity() instanceof EchoOrbEntity
                    || source().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                    || source().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
        }

        boolean meleeBasic() {
            if (magic()) {
                return false;
            }
            Entity direct = source().getDirectEntity();
            Entity attackerEntity = source().getEntity();
            return direct != null && direct == attackerEntity;
        }

        boolean rangedBasic() {
            if (magic()) {
                return false;
            }
            Entity direct = source().getDirectEntity();
            if (!(direct instanceof Projectile projectile)) {
                return false;
            }
            Entity owner = projectile.getOwner();
            Entity attackerEntity = source().getEntity();
            return attackerEntity != null && owner != null && owner.getUUID().equals(attackerEntity.getUUID());
        }
    }

    private static String fmt(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
