package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.GearFxBroadcast;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2026 传说第 2 批装备被动（服务端结算）：
 *
 * <ul>
 *   <li><b>振奋盔甲·无匹活力（vigor）</b>：受到的治疗提升由 Apothic 的 {@code healing_received}
 *       属性原生结算（装备 JSON 引用）；{@b 护盾}部分不在 Apothic 结算范围内，由
 *       {@link ShieldHpService#apply} 读取同一属性做乘区——治疗与护盾语义统一。</li>
 *   <li><b>日炎圣盾·献祭（sunfire）</b>：佩戴期间每秒点燃周围敌人（常驻版 immolate，
 *       复用灼烧服务逐跳结算），自身带常驻火焰特效。</li>
 *   <li><b>饮血剑·余烬（remnant）</b>：生命值已满时的溢出治疗（含生命偷取）转化为
 *       血色护盾（上限配置），持续到期清空。</li>
 *   <li><b>斯特拉克的挑战护手·救主灵刃（lifeline）</b>：本次伤害将把血量压到 30% 以下时
 *       触发——金色护盾（黄心）+ 8 秒 +50 攻击力怒火（MC 基础攻击只有 1 点，
 *       按用户要求改为固定攻击力增益），冷却 90 秒。</li>
 *   <li><b>基克的聚合·聚合风暴（zeal）</b>：攻击命中积累充能，满 8 次释放冰火风暴——
 *       周身敌人受魔法伤害并大幅减速，自身风暴特效，内置冷却 10 秒。</li>
 *   <li><b>海克斯注力刚壁·超速驱动（overdrive）</b>：施放终极技能（铁魔法基础法力
 *       消耗 &gt; 200，与开战弹幕同判定）后 8 秒内近战数值 +50% 攻速（含等额蓄力速度）
 *       与 +20% 移速，内部冷却 30 秒。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolNewLegendPassivesEvents {

    public static final String GEAR_SPIRIT_VISAGE = "spirit_visage";
    public static final String GEAR_SUNFIRE_AEGIS = "sunfire_aegis";
    public static final String GEAR_BLOODTHIRSTER = "bloodthirster";
    public static final String GEAR_STERAKS_GAGE = "steraks_gage";
    public static final String GEAR_ZEKES_CONVERGENCE = "zekes_convergence";
    public static final String GEAR_HEXPLATE = "experimental_hexplate";

    /** Apothic「受到治疗提升」属性（治疗加成由 Apothic 原生结算，护盾由本模组读取乘区）。 */
    private static final String INCOMING_HEAL_ATTR = "attributeslib:healing_received";

    //Hexplate 过载：玩家 UUID → 增益到期毫秒
    private static final Map<UUID, Long> OVERDRIVE_UNTIL_MS = new HashMap<>();
    //斯特拉克救主灵刃节流（官方无冷却，仅 1 秒硬节流防同波多段反复刷盾）
    private static final Map<UUID, Long> STERAKS_NEXT_MS = new HashMap<>();
    //饮血灵液护盾节流（防止每跳吸血都重置）：UUID → 下次可重置毫秒
    private static final Map<UUID, Long> BT_SHIELD_NEXT_MS = new HashMap<>();
    //泽克霜火风暴：UUID → 就绪截止（R 后 5 秒）/ 风暴进行截止 / 冷却下次可用
    private static final Map<UUID, Long> ZEKE_READY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ZEKE_STORM_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ZEKE_NEXT_MS = new HashMap<>();

    private static final UUID OVERDRIVE_AS_MOD = UUID.fromString("ae60d9eb-5c7f-4d24-c8fb-7023b4d5e6f7");
    private static final UUID OVERDRIVE_DS_MOD = UUID.fromString("bf71eafc-6d80-4e35-d9fc-8134c5e6f7a8");
    private static final UUID OVERDRIVE_MS_MOD = UUID.fromString("c082fbad-7e91-4f46-ebad-9245d6f7a8b9");

    private LolNewLegendPassivesEvents() {
    }

    // ------------------------------------------------------------------ //
    // 饮血剑·余烬：满血时的溢出治疗转化为血色护盾
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, GEAR_BLOODTHIRSTER)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_BLOODTHIRSTER, "remnant");
        if (effect == null || !effect.enabled || effect.shield_amount <= 0.0D) {
            return;
        }
        // 仅当生命值已满（治疗将溢出）时转化，避免挤占正常回血
        if (player.getHealth() < player.getMaxHealth() - 0.01D) {
            return;
        }
        long now = System.currentTimeMillis();
        Long next = BT_SHIELD_NEXT_MS.get(player.getUUID());
        if (next != null && now < next) {
            return;
        }
        BT_SHIELD_NEXT_MS.put(player.getUUID(), now + 500L);
        // 已被其它来源护盾占用时不覆盖（覆盖制吸收池），等其到期
        if (ShieldHpService.hasActive(player.getUUID())
                && !ShieldHpService.isSource(player.getUUID(), ShieldHpService.SOURCE_BLOODTHIRSTER)) {
            return;
        }
        float cap = (float) effect.shield_amount;
        long durationMs = Math.round(Math.max(5.0D, effect.duration_seconds) * 1000.0D);
        float current = ShieldHpService.isSource(player.getUUID(), ShieldHpService.SOURCE_BLOODTHIRSTER)
                ? player.getAbsorptionAmount() : 0.0F;
        float amount = Math.min(cap, current + event.getAmount());
        event.setCanceled(true);
        ShieldHpService.apply(player, ShieldHpService.SOURCE_BLOODTHIRSTER, amount, durationMs);
        // 血色护盾特效：与护盾同窗口
        GearFxBroadcast.window(player, FxKind.SHIELD_BLOODTHIRSTER,
                (int) Math.max(20, durationMs / 50L));
    }

    // ------------------------------------------------------------------ //
    // 斯特拉克的挑战护手·救主灵刃（官方口径：无内置冷却；
    // 护盾 = 60% 额外生命值（最大生命 − 原版基础 20 点），4.5 秒内持续衰减）
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onSteraksLifeline(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, GEAR_STERAKS_GAGE)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_STERAKS_GAGE, "lifeline");
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long nextMs = STERAKS_NEXT_MS.get(uuid);
        if (nextMs != null && now < nextMs) {
            return;
        }
        double maxHp = player.getMaxHealth();
        double threshold = maxHp * Math.max(0.0D, effect.trigger_health_percent);
        if (player.getHealth() - event.getAmount() > threshold) {
            return;
        }
        if (player.hasEffect(MobEffects.ABSORPTION)) {
            return; // 外部黄心（金苹果等）在场时不覆盖
        }

        // 官方救主灵刃无冷却，但为避免同一波多段伤害瞬间反复刷盾，保留 1 秒硬节流
        STERAKS_NEXT_MS.put(uuid, now + 1000L);
        long durationMs = Math.round(Math.max(1.0D, effect.duration_seconds) * 1000.0D);

        // 护盾 = 60% 额外生命值（最大生命 − 原版基础 20 点）
        double bonusHealth = Math.max(0.0D, maxHp - 20.0D);
        float shield = (float) (bonusHealth * (effect.shield_amount > 0
                ? effect.shield_amount : 0.60D));
        ShieldHpService.apply(player, ShieldHpService.SOURCE_STERAK, shield, durationMs);
        GearFxBroadcast.window(player, FxKind.SHIELD_STERAK, (int) (durationMs / 50L));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.8F);
        LOLAccessories.LOGGER.info("[斯特拉克] {} 触发救主灵刃：护盾 {}（60% 额外生命）{} 秒",
                player.getName().getString(), fmt(shield), fmt(durationMs / 1000.0D));
    }

    // ------------------------------------------------------------------ //
    // 基克的聚合·霜火风暴（官方口径：施放终极技能后 5 秒内就绪一个风暴；
    // 进入战斗后召唤风暴环绕自身 5 秒，每秒对周围敌人造成魔法伤害并 30% 减速；
    // 内部冷却 45 秒）
    // ------------------------------------------------------------------ //

    /**
     * 铁魔法施放终极技能（基础法力消耗 &gt; 200）时由 {@code IronsLegendCastingEvents} 调用：
     * 佩戴基克的聚合时，5 秒内就绪一个霜火风暴（冷却 45 秒内不就绪）。
     */
    public static void onUltimateCastZeke(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, GEAR_ZEKES_CONVERGENCE)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_ZEKES_CONVERGENCE, "zeal");
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long nextMs = ZEKE_NEXT_MS.get(uuid);
        if (nextMs != null && now < nextMs) {
            return; // 45 秒冷却中，风暴不就绪
        }
        long windowMs = Math.round(Math.max(1.0D, effect.duration_seconds) * 1000.0D);
        ZEKE_READY_UNTIL.put(uuid, now + windowMs);
        LOLAccessories.LOGGER.info("[基克聚合] {} 的霜火风暴已就绪（{} 秒内进入战斗即召唤）",
                player.getName().getString(), fmt(windowMs / 1000.0D));
    }

    /** 风暴召唤与持续伤害（tick 每 1 秒）。 */
    private static void tickZekesStorm(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long readyUntil = ZEKE_READY_UNTIL.get(uuid);
        if (readyUntil == null) {
            return;
        }
        if (now > readyUntil) {
            ZEKE_READY_UNTIL.remove(uuid);
            return;
        }
        if (!LolLegendPassiveEvents.isPlayerInCombat(player)) {
            return; // 进入战斗后才召唤
        }
        ZEKE_READY_UNTIL.remove(uuid);
        GearConfig.OnHitEffect effect = findEffect(GEAR_ZEKES_CONVERGENCE, "zeal");
        if (effect == null || !effect.enabled) {
            return;
        }
        long cooldownMs = Math.round(Math.max(1.0D, effect.cooldown_seconds) * 1000.0D);
        ZEKE_NEXT_MS.put(uuid, now + cooldownMs);
        ZEKE_STORM_UNTIL.put(uuid, now + Math.round(Math.max(1.0D, effect.duration_seconds) * 1000.0D));
        GearFxBroadcast.window(player, FxKind.ZEKES_STORM,
                (int) Math.round(Math.max(1.0D, effect.duration_seconds) * 20.0D));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.7F);
        LOLNetworking.sendSkillCooldown(player, "zekes_convergence",
                (int) (cooldownMs / 50L));
        LOLAccessories.LOGGER.info("[基克聚合] {} 进入战斗，召唤霜火风暴 5 秒（冷却 {}s）",
                player.getName().getString(), fmt(cooldownMs / 1000.0D));
    }

    /** 风暴进行中：每秒对周围敌人造成魔法伤害并 30% 减速。 */
    private static void tickZekesStormDamage(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long until = ZEKE_STORM_UNTIL.get(uuid);
        if (until == null || now >= until) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_ZEKES_CONVERGENCE, "zeal");
        if (effect == null) {
            return;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 4.0D;
        float damage = (float) (effect.base_damage > 0 ? effect.base_damage : 30.0D);
        int slowTicks = 40; // 2 秒 30% 档减速
        List<LivingEntity> enemies = LolNewEpicPassiveEvents.enemiesAround(player, player, radius);
        for (LivingEntity enemy : enemies) {
            // 魔法伤害 = 铁魔法学派伤害
            IronsSpellDamage.apply(player, enemy, damage, IronsSpellDamage.resolve(effect.school));
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1, false, true));
        }
    }

    // ------------------------------------------------------------------ //
    // 海克斯注力刚壁·超速驱动（由终极施法链路调用）
    // ------------------------------------------------------------------ //

    /** 铁魔法施放终极技能（基础法力消耗 &gt; 200）时由 {@code IronsLegendCastingEvents} 调用。 */
    public static void onUltimateCast(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, GEAR_HEXPLATE)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_HEXPLATE, "overdrive");
        if (effect == null || !effect.enabled) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long cooldownMs = Math.round(Math.max(1.0D, effect.cooldown_seconds) * 1000.0D);
        // 内部冷却：从上次超速驱动开始计算（与官方一致），而非从本次施法
        // 简化实现：用增益到期时间 + 冷却差判定
        Long until = OVERDRIVE_UNTIL_MS.get(uuid);
        if (until != null && now < until + cooldownMs - Math.round(Math.max(1.0D, effect.duration_seconds) * 1000.0D)) {
            return;
        }
        double durationMs = Math.max(1.0D, effect.duration_seconds) * 1000.0D;
        // 近战数值：+50% 攻速（含等额蓄力速度）+20% 移速
        double asRatio = effect.amount > 0 ? effect.amount : 0.50D;
        double msRatio = effect.move_speed_ratio > 0 ? effect.move_speed_ratio : 0.20D;
        applyTransient(player, "minecraft:generic.attack_speed", OVERDRIVE_AS_MOD,
                asRatio, "lolaccessories:hexplate_overdrive", true);
        applyTransient(player, "attributeslib:draw_speed", OVERDRIVE_DS_MOD,
                asRatio, "lolaccessories:hexplate_overdrive", false);
        applyTransient(player, "minecraft:generic.movement_speed", OVERDRIVE_MS_MOD,
                msRatio, "lolaccessories:hexplate_overdrive", true);
        OVERDRIVE_UNTIL_MS.put(uuid, now + (long) durationMs);
        GearFxBroadcast.window(player, FxKind.HEXPLATE_OVERDRIVE, (int) (durationMs / 50L));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
        LOLNetworking.sendSkillCooldown(player, "overdrive", (int) (cooldownMs / 50L));
        LOLAccessories.LOGGER.info("[注力刚壁] {} 进入超速驱动：+{}% 攻速 +{}% 移速 {} 秒",
                player.getName().getString(), fmt(asRatio * 100), fmt(msRatio * 100),
                fmt(durationMs / 1000.0D));
    }

    // ------------------------------------------------------------------ //
    // 日炎圣盾·献祭（常驻灼烧）
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        // 每 20 tick（1 秒）结算一次
        if (player.tickCount % 20 != 0) {
            return;
        }
        UUID uuid = player.getUUID();
        // 日炎·献祭：进入战斗后才灼烧（官方口径），每秒 20 + 1.5% 额外生命值
        if (CuriosGearWear.isWearing(player, GEAR_SUNFIRE_AEGIS)
                && LolLegendPassiveEvents.isPlayerInCombat(player)) {
            GearConfig.OnHitEffect effect = findEffect(GEAR_SUNFIRE_AEGIS, "sunfire");
            if (effect != null && effect.enabled) {
                double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D;
                double bonusHealth = Math.max(0.0D, player.getMaxHealth() - 20.0D);
                double dps = effect.base_damage + bonusHealth * Math.max(0.0D, effect.max_health_pct);
                for (LivingEntity enemy : LolNewEpicPassiveEvents.enemiesAround(player, player, radius)) {
                    // 日炎圣盾：火焰学派灼烧
                    LolNewEpicPassiveEvents.startBurn(enemy, dps, 1.2D, "fire");
                }
                // 战斗中火焰特效：窗口滚动续期
                GearFxBroadcast.window(player, FxKind.SUNFIRE_AEGIS, 40);
            }
        }
        // 基克的聚合：风暴就绪判定 + 风暴持续伤害
        tickZekesStorm(player);
        tickZekesStormDamage(player);
        // 到期增益清理
        clearExpired(player, OVERDRIVE_UNTIL_MS, OVERDRIVE_AS_MOD);
        clearExpired(player, OVERDRIVE_UNTIL_MS, OVERDRIVE_DS_MOD);
        clearExpired(player, OVERDRIVE_UNTIL_MS, OVERDRIVE_MS_MOD);
    }

    private static void clearExpired(ServerPlayer player, Map<UUID, Long> untilMap, UUID modifier) {
        Long until = untilMap.get(player.getUUID());
        if (until == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            untilMap.remove(player.getUUID());
            removeTransient(player, modifier);
        }
    }

    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        OVERDRIVE_UNTIL_MS.remove(uuid);
        STERAKS_NEXT_MS.remove(uuid);
        BT_SHIELD_NEXT_MS.remove(uuid);
        ZEKE_READY_UNTIL.remove(uuid);
        ZEKE_STORM_UNTIL.remove(uuid);
        ZEKE_NEXT_MS.remove(uuid);
    }

    // ------------------------------------------------------------------ //
    // 工具
    // ------------------------------------------------------------------ //

    static GearConfig.OnHitEffect findEffect(String gearId, String effectId) {
        GearConfig config = GearConfigManager.get(gearId);
        return config == null ? null : config.findEffect(effectId).orElse(null);
    }

    private static void applyTransient(Player player, String attributeId, UUID uuid,
                                       double value, String name, boolean multiplyBase) {
        Attribute attribute = attributeOf(attributeId);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        instance.addTransientModifier(new AttributeModifier(uuid, name, value,
                multiplyBase ? AttributeModifier.Operation.MULTIPLY_BASE
                        : AttributeModifier.Operation.ADDITION));
    }

    private static void removeTransient(Player player, UUID uuid) {
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
        }
    }

    private static Attribute attributeOf(String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        return id == null ? null : BuiltInRegistries.ATTRIBUTE.get(id);
    }

    private static String fmt(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }
}
