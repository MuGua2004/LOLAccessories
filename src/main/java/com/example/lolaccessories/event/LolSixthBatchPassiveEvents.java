package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 第六批传说装备被动：时光之杖 / 冰脉护手 / 千变者贾修 / 海妖杀手 / 不朽盾弓。
 * 时光之杖的永恒类效果复用现有 eternity 结算（受伤回蓝 + 施法回血）。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolSixthBatchPassiveEvents {

    public static final String GEAR_ROD_OF_AGES = "rod_of_ages";
    public static final String GEAR_ICEBORN = "iceborn_gauntlet";
    public static final String GEAR_JAKSHO = "jaksho";
    public static final String GEAR_KRAKEN = "kraken_slayer";
    public static final String GEAR_SHIELDBOW = "immortal_shieldbow";

    private static final String TIMELESS_STACK_KEY = "rod_of_ages_timeless";
    private static final UUID TIMELESS_HP_UUID =
            UUID.fromString("a1b2c3d4-3336-4a5b-8c6d-000000000036");
    private static final UUID TIMELESS_MANA_UUID =
            UUID.fromString("a1b2c3d4-3337-4a5b-8c6d-000000000037");
    private static final UUID TIMELESS_AP_UUID =
            UUID.fromString("a1b2c3d4-3338-4a5b-8c6d-000000000038");
    private static final UUID JAKSHO_DEF_UUID =
            UUID.fromString("a1b2c3d4-3339-4a5b-8c6d-000000000039");

    private static final Map<UUID, long[]> TIMELESS = new HashMap<>();
    private static final Map<UUID, Long> ICEBORN_READY = new HashMap<>();
    private static final Map<UUID, Long> ICEBORN_CD = new HashMap<>();
    private static final Map<UUID, long[]> JAKSHO_COMBAT = new HashMap<>();
    private static final Map<UUID, long[]> KRAKEN_CHARGE = new HashMap<>();
    private static final Map<UUID, Long> SHIELDBOW_CD = new HashMap<>();

    private LolSixthBatchPassiveEvents() {
    }

    // ------------------------------------------------------------------ //
    // 攻击侧：千变者战斗计时 / 海妖放倒它 / 冰脉咒刃消费
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
        if (!LolNewEpicPassiveEvents.isEnemyOf(attacker, victim)) {
            return;
        }
        // 千变者贾修：造成伤害保持战斗
        if (CuriosGearWear.isWearing(attacker, GEAR_JAKSHO)) {
            markJakshoCombat(attacker, now);
        }
        // 海妖杀手·放倒它：仅弹射物攻击触发（普攻弹道由玩家射出）
        if (CuriosGearWear.isWearing(attacker, GEAR_KRAKEN)
                && source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() != null
                && projectile.getOwner().getUUID().equals(attacker.getUUID())
                && !isMagicDamage(source)) {
            GearConfig config = GearConfigManager.get(GEAR_KRAKEN);
            GearConfig.OnHitEffect effect = config == null
                    ? null : config.findEffect("bring_it_down").orElse(null);
            if (effect != null && effect.enabled) {
                long[] charge = KRAKEN_CHARGE.computeIfAbsent(attacker.getUUID(), k -> new long[2]);
                long window = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 4.0D) * 1000.0D);
                if (now > charge[1]) {
                    charge[0] = 0;
                }
                charge[1] = now + window;
                int max = Math.max(1, effect.max_stacks > 0 ? effect.max_stacks : 2);
                if (charge[0] >= max) {
                    charge[0] = 0; // 第三击：消耗全部充能
                    double missing = Math.max(0.0D, victim.getMaxHealth() - victim.getHealth())
                            / Math.max(1.0D, victim.getMaxHealth());
                    double base = effect.base_damage > 0 ? effect.base_damage : 150.0D;
                    if (effect.bonus_pct > 0) {
                        base += Math.min(60.0D, attacker.experienceLevel * effect.bonus_pct);
                    }
                    double bonus = effect.amount > 0 ? effect.amount : 0.75D;
                    float extra = (float) (base * (1.0D + bonus * missing));
                    event.setAmount(event.getAmount() + extra);
                } else {
                    charge[0]++;
                }
            }
        }
        // 冰脉护手·咒刃：施法后的下一次普攻（额外物理伤害 + 冰冷地带）
        if (CuriosGearWear.isWearing(attacker, GEAR_ICEBORN)
                && source.getDirectEntity() == attacker
                && !isMagicDamage(source)) {
            Long ready = ICEBORN_READY.get(attacker.getUUID());
            Long cd = ICEBORN_CD.get(attacker.getUUID());
            if (ready != null && now <= ready && (cd == null || now >= cd)) {
                GearConfig config = GearConfigManager.get(GEAR_ICEBORN);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("iceborn_spellblade").orElse(null);
                if (effect != null && effect.enabled) {
                    ICEBORN_READY.remove(attacker.getUUID());
                    ICEBORN_CD.put(attacker.getUUID(), now + Math.round(
                            (effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 1.5D) * 1000.0D));
                    // 额外物理伤害：150% 攻击力
                    float extra = (float) ((effect.amount > 0 ? effect.amount : 1.5D)
                            * attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    event.setAmount(event.getAmount() + extra);
                    // 冰冷地带：目标周围半径内敌人减速（近战 25% / 远程 12.5%），2 秒
                    double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D;
                    double slow = isRangedWeapon(attacker)
                            ? (effect.melee_ratio > 0 ? effect.melee_ratio : 0.125D)
                            : (effect.bonus_pct > 0 ? effect.bonus_pct : 0.25D);
                    int dur = (int) Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 20.0D);
                    int amp = Math.max(0, (int) Math.round(slow / 0.15D) - 1);
                    for (LivingEntity e : victim.level().getEntitiesOfClass(LivingEntity.class,
                            victim.getBoundingBox().inflate(radius),
                            t -> t != attacker && t.isAlive()
                                    && LolNewEpicPassiveEvents.isEnemyOf(attacker, t))) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, amp, true, false));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 受害侧：千变者战斗计时 / 不朽盾弓·救主灵刃
    // ------------------------------------------------------------------ //

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurtVictim(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (CuriosGearWear.isWearing(player, GEAR_JAKSHO)) {
            markJakshoCombat(player, now);
        }
        // 不朽盾弓·救主灵刃：将使生命值跌到 30% 以下的伤害 → 白盾 3 秒（90 秒冷却）
        if (CuriosGearWear.isWearing(player, GEAR_SHIELDBOW)) {
            Long cd = SHIELDBOW_CD.get(player.getUUID());
            if (cd == null || now >= cd) {
                GearConfig config = GearConfigManager.get(GEAR_SHIELDBOW);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("shieldbow_lifeline").orElse(null);
                if (effect != null && effect.enabled) {
                    float amount = event.getAmount();
                    float threshold = (float) (player.getMaxHealth()
                            * (effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.30D));
                    if (player.getHealth() - amount <= threshold) {
                        SHIELDBOW_CD.put(player.getUUID(), now + Math.round(
                                (effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 90.0D) * 1000.0D));
                        ShieldHpService.apply(player, "shieldbow",
                                (float) (effect.shield_amount > 0 ? effect.shield_amount : 550.0D),
                                Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D) * 1000.0D),
                                ShieldType.WHITE);
                    }
                }
            }
        }
    }

    private static void markJakshoCombat(ServerPlayer player, long now) {
        long[] state = JAKSHO_COMBAT.computeIfAbsent(player.getUUID(), k -> new long[2]);
        if (state[0] == 0) {
            state[0] = now; // 战斗开始
        }
        state[1] = now;     // 最后战斗时刻
    }

    // ------------------------------------------------------------------ //
    // 周期维护：时光叠层 / 千变者防御激活
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 时光之杖·时无级：每 60 秒 1 层（至多 10 层，层数随玩家存档持久化）
            if (CuriosGearWear.isWearing(player, GEAR_ROD_OF_AGES)) {
                GearConfig config = GearConfigManager.get(GEAR_ROD_OF_AGES);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("timeless").orElse(null);
                if (effect != null && effect.enabled) {
                    long[] state = TIMELESS.computeIfAbsent(player.getUUID(), k -> new long[2]);
                    int stacks = StackedGearState.addStacks(player, TIMELESS_STACK_KEY, 0,
                            effect.max_stacks > 0 ? effect.max_stacks : 10);
                    if (now >= state[0]) {
                        state[0] = now + Math.round(
                                (effect.interval_seconds > 0 ? effect.interval_seconds : 60.0D) * 1000.0D);
                        if (stacks < (effect.max_stacks > 0 ? effect.max_stacks : 10)) {
                            stacks = StackedGearState.addStacks(player, TIMELESS_STACK_KEY, 1,
                                    effect.max_stacks > 0 ? effect.max_stacks : 10);
                        }
                    }
                    double hpPer = effect.amount > 0 ? effect.amount : 10.0D;
                    double manaPer = effect.base_damage > 0 ? effect.base_damage : 30.0D;
                    double apPer = effect.bonus_pct > 0 ? effect.bonus_pct : 0.03D;
                    applyTransient(player, Attributes.MAX_HEALTH, TIMELESS_HP_UUID, hpPer * stacks);
                    applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                            .getValue(new net.minecraft.resources.ResourceLocation(
                                    "irons_spellbooks", "max_mana")), TIMELESS_MANA_UUID, manaPer * stacks);
                    applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                            .getValue(new net.minecraft.resources.ResourceLocation(
                                    "irons_spellbooks", "spell_power")), TIMELESS_AP_UUID, apPer * stacks);
                    // 满层奖励：英雄等级提升 1 级（一次性）
                    if (stacks >= (effect.max_stacks > 0 ? effect.max_stacks : 10) && state[1] == 0) {
                        state[1] = 1;
                        player.giveExperienceLevels(1);
                    }
                } else {
                    TIMELESS.remove(player.getUUID());
                    applyTransient(player, Attributes.MAX_HEALTH, TIMELESS_HP_UUID, 0);
                    applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                            .getValue(new net.minecraft.resources.ResourceLocation(
                                    "irons_spellbooks", "max_mana")), TIMELESS_MANA_UUID, 0);
                    applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                            .getValue(new net.minecraft.resources.ResourceLocation(
                                    "irons_spellbooks", "spell_power")), TIMELESS_AP_UUID, 0);
                }
            } else {
                TIMELESS.remove(player.getUUID());
                applyTransient(player, Attributes.MAX_HEALTH, TIMELESS_HP_UUID, 0);
                applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                        .getValue(new net.minecraft.resources.ResourceLocation(
                                "irons_spellbooks", "max_mana")), TIMELESS_MANA_UUID, 0);
                applyTransient(player, net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                        .getValue(new net.minecraft.resources.ResourceLocation(
                                "irons_spellbooks", "spell_power")), TIMELESS_AP_UUID, 0);
            }
            // 千变者贾修：战斗满 5 秒后激活 +30% 护甲/魔抗，脱战 5 秒移除
            if (CuriosGearWear.isWearing(player, GEAR_JAKSHO)) {
                GearConfig config = GearConfigManager.get(GEAR_JAKSHO);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("protean").orElse(null);
                long[] state = JAKSHO_COMBAT.get(player.getUUID());
                boolean active = false;
                if (effect != null && effect.enabled && state != null && state[0] > 0) {
                    long window = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D) * 1000.0D);
                    boolean inCombat = now - state[1] <= window;
                    active = inCombat && now - state[0] >= window;
                    if (!inCombat) {
                        state[0] = 0;
                    }
                }
                double ratio = active ? (effect != null && effect.amount > 0 ? effect.amount : 0.30D) : 0.0D;
                applyMultiplier(player, Attributes.ARMOR, JAKSHO_DEF_UUID, ratio);
                applyMultiplier(player, ModAttributes.LOL_MAGIC_RESIST.get(), JAKSHO_DEF_UUID, ratio);
            } else {
                JAKSHO_COMBAT.remove(player.getUUID());
                applyMultiplier(player, Attributes.ARMOR, JAKSHO_DEF_UUID, 0);
                applyMultiplier(player, ModAttributes.LOL_MAGIC_RESIST.get(), JAKSHO_DEF_UUID, 0);
            }
        }
        if (SHIELDBOW_CD.size() > 64) {
            SHIELDBOW_CD.entrySet().removeIf(e -> System.currentTimeMillis() >= e.getValue());
        }
        if (ICEBORN_CD.size() > 64) {
            ICEBORN_CD.entrySet().removeIf(e -> System.currentTimeMillis() >= e.getValue());
        }
        if (ICEBORN_READY.size() > 64) {
            ICEBORN_READY.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue());
        }
    }

    private static void applyTransient(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                       UUID id, double value) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
        if (value > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, "lolaccessories_sixth",
                    value, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void applyMultiplier(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                        UUID id, double ratio) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
        if (ratio > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, "lolaccessories_sixth_mult",
                    ratio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        TIMELESS.remove(event.getEntity().getUUID());
        ICEBORN_READY.remove(event.getEntity().getUUID());
        ICEBORN_CD.remove(event.getEntity().getUUID());
        JAKSHO_COMBAT.remove(event.getEntity().getUUID());
        KRAKEN_CHARGE.remove(event.getEntity().getUUID());
        SHIELDBOW_CD.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ //
    // 工具
    // ------------------------------------------------------------------ //

    private static boolean isMagicDamage(DamageSource source) {
        boolean irons = source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks"))
                .orElse(false);
        return irons || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    private static boolean isRangedWeapon(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
    }

    /** 铁魔法施法监听（仅铁魔法安装时由 IronsCompat 动态注册）：冰脉咒刃的施法标记。 */
    public static final class IcebornCastListener {
        private IcebornCastListener() {
        }

        public static void register() {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(IcebornCastListener.class);
        }

        @SubscribeEvent
        public static void onSpellCast(io.redspace.ironsspellbooks.api.events.SpellOnCastEvent event) {
            if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (CuriosGearWear.isWearing(player, GEAR_ICEBORN)) {
                ICEBORN_READY.put(player.getUUID(), System.currentTimeMillis() + 10_000L);
            }
        }
    }
}
