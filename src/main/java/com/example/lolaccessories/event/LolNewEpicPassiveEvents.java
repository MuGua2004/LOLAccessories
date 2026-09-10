package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.IronsMagicBridge;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 34 件新 2 级（史诗）装备的机制事件（服务端结算）。
 *
 * <p>这里集中处理以“佩戴者”为对象、以装备配置 on_hit_effects 为参数驱动的被动：
 *
 * <ul>
 *   <li>重伤（死刑宣告物理 / 湮灭宝珠魔法）：降低目标受到的治疗（{@link ModMobEffects#WOUNDS}）。</li>
 *   <li>灼烧（棘刺背心之外的引燃：斑比的熔渣 immolate / 命定灰烬 inflame）：以灼烧服务逐跳结算。</li>
 *   <li>永恒（万世催化石）：受伤回复法力；造成魔法伤害视作施法命中并回复生命。</li>
 *   <li>磐石（守望者铠甲）：最终伤害固定减免，上限为本次伤害的 20%。</li>
 *   <li>尖刺（棘刺背心）：被攻击命中时向攻击者反弹伤害并对其施加重伤。</li>
 *   <li>疯狂（幽魂面具）：战斗中逐步获得至多 3 层、每层 2% 的伤害加成。</li>
 *   <li>狂怒（净蚀）：对敌攻击后获得 2 秒移速加成（{@link ModMobEffects#RAGE}）。</li>
 *   <li>咒刃（耀光）：魔法命中后使下一次攻击附加额外物理伤害。</li>
 *   <li>顺劈（提亚马特）：物理攻击顺劈目标周围敌人。</li>
 *   <li>充能/牛眼（海克斯科技发电机 / 斥候弹弓）：周期性附加魔法伤害。</li>
 *   <li>生命残片（海克斯饮魔刀）：受魔法伤害生命低于 30% 时获得护盾（生命提升效果形态）。</li>
 *   <li>法盾（翠绿屏障）：格挡一次来自他人的魔法伤害后进入冷却。</li>
 *   <li>启迪（遗失的章节）：升级时回复 20% 最大法力（3 秒内分摊）。</li>
 * </ul>
 *
 * <p>主动（净化 / 新月）与按键链路见 {@link LolNewActiveSkillEvents}。
 * 治疗与护盾强度属性（{@link ModAttributes#LOL_HEAL_POWER}）对本文件内我们亲手给出的治疗与
 * 护盾生效；第三方模组（如铁魔法）的回复不在本模组作用域内。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolNewEpicPassiveEvents {

    /** 灼烧实体 → 灼烧状态（dps = 每秒魔法伤害）。 */
    private static final Map<LivingEntity, BurnState> BURNS = new HashMap<>();
    /** 充能/牛眼等“每 X 秒附伤”的触发冷却：UUID → effectId → 下次可用毫秒。 */
    private static final Map<UUID, Map<String, Long>> PROC_CD_MS = new HashMap<>();
    /** 引燃（immolate）触发节流：UUID → 下次可用毫秒。 */
    private static final Map<UUID, Long> IMMOLATE_CD_MS = new HashMap<>();
    /** 疯狂：UUID → 战斗叠加状态。 */
    private static final Map<UUID, MadnessState> MADNESS = new HashMap<>();
    /** 咒刃就绪：UUID → true。 */
    private static final Map<UUID, Boolean> SPELLBLADE_READY = new HashMap<>();
    /** 咒刃锁定：UUID → 锁定截止毫秒。 */
    private static final Map<UUID, Long> SPELLBLADE_LOCK_MS = new HashMap<>();
    /** 生命残片冷却：UUID → 下次可用毫秒。 */
    private static final Map<UUID, Long> LIFELINE_CD_MS = new HashMap<>();
    /** 法盾：UUID → 是否就绪（true=可格挡一次）。 */
    private static final Map<UUID, Boolean> ANNUL_READY = new HashMap<>();
    /** 法盾冷却：UUID → 下次就绪毫秒。 */
    private static final Map<UUID, Long> ANNUL_CD_MS = new HashMap<>();
    /** 启迪法力分流：UUID → 剩余状态。 */
    private static final Map<UUID, ManaSurge> MANA_SURGE = new HashMap<>();
    /** 升级检测：UUID → 上一秒看到的总经验等级。 */
    private static final Map<UUID, Integer> LAST_LEVEL = new HashMap<>();
    /**
     * 被动二次伤害派发中标记（顺劈溅射、荆棘反伤等）。置真期间不再结算「攻击方被动」，
     * 防止顺劈溅射命中后经嵌套 LivingHurtEvent 再次触发顺劈，形成递归放大。
     * 服务端事件单线程执行，普通 boolean 即可。
     */
    private static boolean secondaryDamageDispatching = false;

    private LolNewEpicPassiveEvents() {
    }

    // ------------------------------------------------------------------ //
    // 主动路径与烧灼跳结算
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick % 10 != 0) {
            return;
        }
        if (!BURNS.isEmpty()) {
            // 对快照迭代：结算跳伤害时会派发 LivingHurtEvent，其回调（如被灼烧者佩戴引燃饰品）
            // 可能同步触发 startBurn 改写 BURNS；若直接边迭代边 hurt 会抛
            // ConcurrentModificationException。每 10 tick 只有燃烧目标非空才拷贝，代价可忽略。
            List<Map.Entry<LivingEntity, BurnState>> snapshot = new ArrayList<>(BURNS.entrySet());
            for (Map.Entry<LivingEntity, BurnState> entry : snapshot) {
                LivingEntity target = entry.getKey();
                BurnState burn = entry.getValue();
                if (target.isRemoved() || !target.isAlive() || target.level().isClientSide) {
                    BURNS.remove(target);
                    continue;
                }
                // 灼烧是魔法伤害 = 铁魔法学派伤害，学派沿用施加时记录的 school
                IronsSpellDamage.apply(null, target, (float) (burn.dps / 2.0D),
                        IronsSpellDamage.resolve(burn.school));
                burn.remainingTicks -= 10;
                if (burn.remainingTicks <= 0) {
                    BURNS.remove(target);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 受伤 / 最终伤害 / 攻击 / 治疗 / 经验等级
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        float original = event.getAmount();
        if (original <= 0.0F) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity sourceEntity = source == null ? null : source.getEntity();
        LivingEntity attacker = sourceEntity instanceof LivingEntity living ? living : null;

        // 受到伤害的一方：受伤触发的被动
        if (victim instanceof Player wornPlayer) {
            handleVictimSide(wornPlayer, attacker, source, original, event);
        }
        // 造成伤害的一方：命中/攻击触发的被动（amount 可能已被磐石等改动，这里统一用原值）
        if (attacker instanceof Player dealPlayer && attacker != victim) {
            handleAttackerSide(dealPlayer, victim, source, event, original);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!(victim instanceof Player player)) {
            return;
        }
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            GearConfig config = GearConfigManager.get(gear.getGearId());
            if (config == null) {
                return;
            }
            for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                if (!effect.enabled) {
                    continue;
                }
                switch (effect.id) {
                    // 磐石（守望者铠甲）：在最终伤害上减免 min(固定值, 本次伤害×上限比例)
                    case "rock_solid" -> {
                        if (event.isCanceled() || event.getAmount() <= 0.0F) {
                            return;
                        }
                        double cap = event.getAmount() * (effect.reduction_cap_pct > 0 ? effect.reduction_cap_pct : 0.0D);
                        double reduce = Math.min(effect.reduction_flat, cap);
                        if (reduce > 0.0D) {
                            float after = Math.max(0.0F, event.getAmount() - (float) reduce);
                            event.setAmount(after);
                        }
                    }
                    // 生命残片（海克斯饮魔刀）
                    case "lifeline" -> tryLifeline(player, victim, source, event, effect);
                    default -> {
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity healed = event.getEntity();
        MobEffectInstance wounds = healed.getEffect(ModMobEffects.WOUNDS.get());
        if (wounds == null || event.getAmount() <= 0.0F) {
            return;
        }
        // 40% → 等级 3；0.1 一档，最多 100%
        int level = Math.max(0, wounds.getAmplifier());
        double cut = Math.min(1.0D, (level + 1) * 0.1D);
        float reduced = Math.max(0.0F, event.getAmount() * (float) (1.0D - cut));
        if (reduced <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(reduced);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living) || !isEnemyOf(player, living)) {
            return;
        }
        // 咒刃在攻击命中前先“待命”——实际附伤放在 onLivingHurt 结算，
        // 这里只负责给净蚀的狂暴施加移动速度效果
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            GearConfig config = GearConfigManager.get(gear.getGearId());
            if (config == null) {
                return;
            }
            for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                if (!effect.enabled) {
                    continue;
                }
                if ("rage".equals(effect.id)) {
                    int ticks = Math.max(5, Math.round((float) effect.duration_seconds * 20.0F));
                    player.addEffect(new MobEffectInstance(ModMobEffects.RAGE.get(), ticks, 0, false, true));
                }
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        UUID uuid = player.getUUID();
        // 疯狂状态的战斗窗口衰减（约每秒一次结算）
        if (player.tickCount % 20 == 0) {
            MadnessState state = MADNESS.get(uuid);
            if (state != null) {
                state.windowTicks -= 20;
                if (state.windowTicks <= 0) {
                    MADNESS.remove(uuid);
                }
            }
        }
        // 法盾：进入就绪（首次佩戴无冷却记录直接就绪；格挡后冷却结束自动重新就绪）
        if (player.tickCount % 20 == 0 && !Boolean.TRUE.equals(ANNUL_READY.get(uuid))) {
            Long cdUntil = ANNUL_CD_MS.get(uuid);
            if (cdUntil == null || System.currentTimeMillis() >= cdUntil) {
                ANNUL_READY.put(uuid, Boolean.TRUE);
                ANNUL_CD_MS.remove(uuid);
            }
        }
        // 升级检测：遗失的章节
        Integer last = LAST_LEVEL.get(uuid);
        int current = player.experienceLevel;
        if (last != null && current > last) {
            startEnlighten(player);
        }
        LAST_LEVEL.put(uuid, current);
        // 启迪：法力分流到账
        ManaSurge surge = MANA_SURGE.get(uuid);
        if (surge != null && surge.remainingTicks > 0 && player.tickCount % 10 == 0) {
            double step = surge.total / surge.totalTicks * 10.0D;
            IronsMagicBridge.addMana(player, (float) step);
            surge.remainingTicks -= 10;
            if (surge.remainingTicks <= 0) {
                MANA_SURGE.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        PROC_CD_MS.remove(uuid);
        IMMOLATE_CD_MS.remove(uuid);
        MADNESS.remove(uuid);
        SPELLBLADE_READY.remove(uuid);
        SPELLBLADE_LOCK_MS.remove(uuid);
        LIFELINE_CD_MS.remove(uuid);
        ANNUL_READY.remove(uuid);
        ANNUL_CD_MS.remove(uuid);
        MANA_SURGE.remove(uuid);
        LAST_LEVEL.remove(uuid);
    }

    // ------------------------------------------------------------------ //
    // 受害者佩戴的装备被动
    // ------------------------------------------------------------------ //

    private static void handleVictimSide(Player wearer, LivingEntity attacker,
                                         DamageSource source, float amount,
                                         LivingHurtEvent event) {
        CuriosGearWear.forEachEquippedGear(wearer, gear -> {
            GearConfig config = GearConfigManager.get(gear.getGearId());
            if (config == null) {
                return;
            }
            for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                if (!effect.enabled) {
                    continue;
                }
                switch (effect.id) {
                    // 永恒（万世催化石）：受伤回复法力（仅来自生物的伤害计费）
                    case "eternity" -> {
                        if (attacker != null && attacker != wearer) {
                            restoreManaOnHurt(wearer, amount, effect);
                        }
                    }
                    // 尖刺（棘刺背心）：被攻击反弹伤害并施加重伤
                    case "thorns" -> reflectThorns(wearer, attacker, source, effect);
                    // 法盾（翠绿屏障）：格挡一次他人魔法伤害
                    case "annul" -> annulSpell(wearer, attacker, source, event, effect);
                    // 引燃（斑比的熔渣）：受到伤害时点燃周围敌人
                    case "immolate" -> burnNearby(wearer, effect);
                    default -> {
                    }
                }
            }
        });
    }

    private static void restoreManaOnHurt(Player wearer, float damage, GearConfig.OnHitEffect effect) {
        if (damage <= 0.0F || !IronsMagicBridge.isAvailable() || effect.amount <= 0.0D) {
            return;
        }
        float restore = (float) (damage * effect.amount);
        if (restore > 0.0F && IronsMagicBridge.addMana(wearer, restore)) {
            LOLAccessories.LOGGER.debug("[永恒] {} 受伤 {} 点，回复法力 {}", wearer.getName().getString(), damage, restore);
        }
    }

    private static void reflectThorns(Player wearer, LivingEntity attacker, DamageSource source,
                                      GearConfig.OnHitEffect effect) {
        if (attacker == null || attacker == wearer || attacker.isDeadOrDying()) {
            return;
        }
        if (!isBasicPhysical(source) || source.is(DamageTypes.THORNS)) {
            return;
        }
        if (effect.amount <= 0.0D) {
            return;
        }
        boolean was = secondaryDamageDispatching;
        secondaryDamageDispatching = true;
        try {
            attacker.hurt(wearer.level().damageSources().thorns(wearer), (float) effect.amount);
        } finally {
            secondaryDamageDispatching = was;
        }
        // 施加重伤（棘刺背心参数：bonus_pct 为重伤削减比例）
        double severity = effect.bonus_pct > 0 ? effect.bonus_pct : 0.4D;
        int ticks = Math.max(10, Math.round((float) effect.duration_seconds * 20.0F));
        applyWounds(attacker, severity, ticks);
    }

    private static void annulSpell(Player wearer, LivingEntity attacker, DamageSource source,
                                   LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        if (attacker == null || attacker == wearer || !isMagicDamage(source)) {
            return;
        }
        if (!Boolean.TRUE.equals(ANNUL_READY.get(wearer.getUUID()))) {
            return;
        }
        event.setCanceled(true);
        ANNUL_READY.put(wearer.getUUID(), Boolean.FALSE);
        long cdMs = Math.round((effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D) * 1000.0D);
        ANNUL_CD_MS.put(wearer.getUUID(), System.currentTimeMillis() + cdMs);
    }

    // ------------------------------------------------------------------ //
    // 造成伤害方佩戴的装备被动
    // ------------------------------------------------------------------ //

    private static void handleAttackerSide(Player wearer, LivingEntity victim, DamageSource source,
                                           LivingHurtEvent event, float original) {
        // 本模组被动派发的二次伤害（顺劈溅射、荆棘反伤命中）不再触发攻击方被动，
        // 否则溅射命中其他生物会再次走进 handleAttackerSide 再次顺劈，递归放大
        if (secondaryDamageDispatching) {
            return;
        }
        // 先整备“疯狂”：计算时应包含本次增伤
        if (!(victim.isDeadOrDying())) {
            CuriosGearWear.forEachEquippedGear(wearer, gear -> {
                GearConfig config = GearConfigManager.get(gear.getGearId());
                if (config == null) {
                    return;
                }
                for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                    if (!effect.enabled) {
                        continue;
                    }
                    switch (effect.id) {
                        case "grievous_wounds_phys" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                applyWoundsFromConfig(victim, effect);
                            }
                        }
                        case "grievous_wounds_magic" -> {
                            if (isMagicDamage(source) && isEnemyOf(wearer, victim)) {
                                applyWoundsFromConfig(victim, effect);
                            }
                        }
                        // 凡性的提醒：物理伤害（近战直击 / 弹射物）命中后施加重伤。
                        // 铁魔法法术伤害虽属物理口径，但按本模组规则不计入物理触发
                        // （已在 isBasicPhysical 中统一排除）
                        case "grievous_wounds_physical" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                applyWoundsFromConfig(victim, effect);
                            }
                        }
                        case "inflame" -> {
                            if (isMagicDamage(source) && isEnemyOf(wearer, victim)) {
                                startBurn(victim, effect.amount,
                                        Math.max(1.0D, effect.duration_seconds), effect.school);
                            }
                        }
                        case "revved", "bullseye" -> {
                            if (isEnemyOf(wearer, victim)) {
                                procPeriodicMagic(wearer, victim, effect);
                            }
                        }
                        case "spellblade" -> handleSpellblade(wearer, victim, source, event, effect);
                        case "cleave" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                cleaveAoe(wearer, victim, effect);
                            }
                        }
                        case "immolate" -> burnNearby(wearer, effect);
                        case "madness" -> handleMadness(wearer, victim, event, effect);
                        // 永恒·施法命中：仅“造成魔法伤害命中敌方”时视为施法成功并回血
                        case "eternity" -> {
                            if (isMagicDamage(source) && isEnemyOf(wearer, victim)) {
                                healOnCast(wearer, effect);
                            }
                        }
                        default -> {
                        }
                    }
                }
            });
        }
    }

    /** 按装备配置施加重伤（amount 为削减比例，0.4 = 40%）。 */
    private static void applyWoundsFromConfig(LivingEntity target, GearConfig.OnHitEffect effect) {
        double severity = effect.amount > 0 ? effect.amount : 0.4D;
        int ticks = Math.max(10, Math.round((float) effect.duration_seconds * 20.0F));
        applyWounds(target, severity, ticks);
    }

    /** 施加重伤：按削减比例换算效果等级（0.1 一档）。 */
    private static void applyWounds(LivingEntity target, double severity, int ticks) {
        int level = (int) Math.max(0, Math.min(9, Math.round(severity / 0.1D - 1.0D)));
        target.removeEffect(ModMobEffects.WOUNDS.get());
        target.addEffect(new MobEffectInstance(ModMobEffects.WOUNDS.get(), ticks, level, false, true));
    }

    /** 点燃目标（供同包装备复用：斑比熔渣/命定灰烬/日炎圣盾）。 */
    static void startBurn(LivingEntity target, double dps, double seconds) {
        startBurn(target, dps, seconds, "");
    }

    /**
     * 施加灼烧。{@code school} 为该灼烧的魔法学派（本模组魔法伤害一律是铁魔法学派伤害），
     * 留空时由 {@link IronsSpellDamage#resolve} 退回默认学派。
     */
    static void startBurn(LivingEntity target, double dps, double seconds, String school) {
        if (target == null || target.isDeadOrDying() || dps <= 0.0D) {
            return;
        }
        int ticks = Math.max(5, Math.round((float) seconds * 20.0F));
        BurnState state = BURNS.get(target);
        if (state == null) {
            BURNS.put(target, new BurnState(dps, ticks, school));
        } else {
            if (dps >= state.dps) {
                // 伤害更高的一层灼烧接管学派
                state.school = school;
            }
            state.dps = Math.max(state.dps, dps);
            state.remainingTicks = Math.max(state.remainingTicks, ticks);
        }
        if (BURNS.size() > 256) {
            BURNS.entrySet().removeIf(e -> e.getKey().isRemoved() || !e.getKey().isAlive());
        }
    }

    /** 引燃：点燃佩戴者周围敌对生物。 */
    private static void burnNearby(LivingEntity wearer, GearConfig.OnHitEffect effect) {
        if (wearer.level().isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID uuid = wearer.getUUID();
        Long cd = IMMOLATE_CD_MS.get(uuid);
        if (cd != null && now < cd) {
            return;
        }
        IMMOLATE_CD_MS.put(uuid, now + 500L);
        if (IMMOLATE_CD_MS.size() > 256) {
            IMMOLATE_CD_MS.entrySet().removeIf(e -> now - e.getValue() > 5000L);
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D;
        for (LivingEntity e : enemiesAround(wearer, wearer, radius)) {
            startBurn(e, effect.amount, Math.max(1.0D, effect.duration_seconds), effect.school);
        }
    }

    /** 充能 / 牛眼：每 cooldown_seconds 秒触发一次额外魔法伤害。 */
    private static void procPeriodicMagic(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        double cdSeconds = effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 12.0D;
        long cdMs = Math.round(cdSeconds * 1000.0D);
        long now = System.currentTimeMillis();
        Map<String, Long> cds = PROC_CD_MS.computeIfAbsent(wearer.getUUID(), k -> new HashMap<>());
        Long until = cds.get(effect.id);
        if (until != null && now < until) {
            return;
        }
        double base = effect.base_damage > 0 ? effect.base_damage : 8.0D;
        cds.put(effect.id, now + cdMs);
        // 魔法伤害 = 铁魔法学派伤害，学派由该效果的 school 字段决定（见 IronsSpellDamage）
        IronsSpellDamage.apply(wearer, victim, (float) base, IronsSpellDamage.resolve(effect.school));
    }

    private static void handleSpellblade(Player wearer, LivingEntity victim, DamageSource source,
                                         LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        if (isMagicDamage(source)) {
            // 魔法命中（视为一次施法）：标记咒刃就绪（内置 1.5 秒锁防止连发术连续触发）
            long now = System.currentTimeMillis();
            Long lockUntil = SPELLBLADE_LOCK_MS.get(wearer.getUUID());
            if (lockUntil == null || now >= lockUntil) {
                SPELLBLADE_READY.put(wearer.getUUID(), Boolean.TRUE);
                SPELLBLADE_LOCK_MS.put(wearer.getUUID(), now + Math.round(
                        (effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 1.5D) * 1000.0D));
            }
            return;
        }
        if (!isBasicPhysical(source)) {
            return;
        }
        if (!Boolean.TRUE.equals(SPELLBLADE_READY.get(wearer.getUUID()))) {
            return;
        }
        SPELLBLADE_READY.put(wearer.getUUID(), Boolean.FALSE);
        double ad = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double extra = effect.base_damage + ad * (effect.power_ratio > 0 ? effect.power_ratio : 1.0D);
        if (extra > 0.0D) {
            event.setAmount(event.getAmount() + (float) extra);
        }
    }

    /** 顺劈：物理攻击命中目标后，对其周围敌对生物造成佩戴者攻击力×比例 的物理溅射。 */
    private static void cleaveAoe(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 2.0D;
        double ratio = effect.amount > 0 ? effect.amount : 0.6D;
        double ad = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float splash = (float) Math.max(1.0D, ad * ratio);
        DamageSource physical = wearer.level().damageSources().playerAttack(wearer);
        boolean was = secondaryDamageDispatching;
        secondaryDamageDispatching = true;
        try {
            for (LivingEntity enemy : enemiesAround(victim, wearer, radius)) {
                if (enemy != victim) {
                    enemy.hurt(physical, splash);
                }
            }
        } finally {
            secondaryDamageDispatching = was;
        }
    }

    private static void handleMadness(Player wearer, LivingEntity victim, LivingHurtEvent event,
                                      GearConfig.OnHitEffect effect) {
        UUID uuid = wearer.getUUID();
        double windowSeconds = effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D;
        MadnessState state = MADNESS.computeIfAbsent(uuid, k -> new MadnessState(0));
        state.windowTicks = Math.round((float) windowSeconds * 20.0F);
        int max = (int) Math.max(1, effect.max_stacks);
        if (state.stacks < max) {
            state.stacks++;
        }
        // 每次命中带来的增伤：层数×amount（amount 默认 0.02 = 每层 2%）
        double amountPct = effect.amount > 0 ? effect.amount : 0.02D;
        double bonus = state.stacks * amountPct;
        if (bonus > 0.0D && !event.isCanceled()) {
            event.setAmount(event.getAmount() * (float) (1.0D + bonus));
        }
        if (MADNESS.size() > 64) {
            // 兜底：窗口已结束的疯狂状态直接清理，避免长时间无人维护
            MADNESS.entrySet().removeIf(entry -> entry.getValue().windowTicks <= 0);
        }
    }

    /** 永恒·施法命中：对自身回复生命（参考法力消耗 × bonus_pct，受治疗护盾强度加成）。 */
    private static void healOnCast(Player wearer, GearConfig.OnHitEffect effect) {
        if (effect.bonus_pct <= 0.0D) {
            return;
        }
        double refMana = effect.base_damage > 0 ? effect.base_damage : 100.0D;
        double amount = refMana * effect.bonus_pct * (1.0D + healPowerBonus(wearer));
        if (amount > 0.0D) {
            wearer.heal((float) amount);
        }
    }

    /** 启迪：升级时按 20% 最大法力分 3 秒补充。 */
    private static void startEnlighten(Player player) {
        GearConfig config = GearConfigManager.get("lost_chapter");
        if (config == null || !CuriosGearWear.isWearing(player, "lost_chapter")) {
            return;
        }
        GearConfig.OnHitEffect effect = config.findEffect("enlighten").orElse(null);
        if (effect == null || !effect.enabled || !IronsMagicBridge.isAvailable()) {
            return;
        }
        double ratio = effect.amount > 0 ? effect.amount : 0.2D;
        float maxMana = IronsMagicBridge.getMaxMana(player);
        if (maxMana <= 0.0F) {
            return;
        }
        double total = maxMana * ratio * (1.0D + healPowerBonus(player));
        double seconds = Math.max(0.5D, effect.duration_seconds);
        int totalTicks = Math.max(10, Math.round((float) seconds * 20.0F));
        MANA_SURGE.put(player.getUUID(), new ManaSurge(total, totalTicks));
    }

    /** 生命残片：受魔法伤害且生命将跌破 30% 时，给出 2.5 秒黄心魔法护盾（非药水效果形态）。 */
    private static void tryLifeline(Player player, LivingEntity victim, DamageSource source,
                                    LivingDamageEvent event, GearConfig.OnHitEffect effect) {
        if (!isMagicDamage(source)) {
            return;
        }
        UUID uuid = player.getUUID();
        Long cd = LIFELINE_CD_MS.get(uuid);
        if (cd != null && System.currentTimeMillis() < cd) {
            return;
        }
        double threshold = effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.3D;
        float damage = event.getAmount();
        float healthAfter = player.getHealth() - damage;
        if (healthAfter > player.getMaxHealth() * (float) threshold) {
            return;
        }
        if (player.getAbsorptionAmount() > 0.0F) {
            return;
        }
        double base = effect.shield_amount > 0 ? effect.shield_amount : 150.0D;
        double shield = base * (1.0D + healPowerBonus(player));
        double seconds = effect.duration_seconds > 0 ? effect.duration_seconds : 2.5D;
        // 黄心护盾：直接写吸收值（金色心），不挂药水效果，避免被清除/叠加异常
        if (player instanceof ServerPlayer serverPlayer) {
            ShieldHpService.apply(serverPlayer, ShieldHpService.SOURCE_LIFELINE,
                    (float) shield, Math.round(seconds * 1000.0D));
        }
        long cdMs = Math.round((effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D) * 1000.0D);
        LIFELINE_CD_MS.put(uuid, System.currentTimeMillis() + cdMs);
    }

    // ------------------------------------------------------------------ //
    // 通用工具
    // ------------------------------------------------------------------ //

    private static double healPowerBonus(LivingEntity entity) {
        double value = entity.getAttributeValue(ModAttributes.LOL_HEAL_POWER.get());
        return Math.max(0.0D, value);
    }

    private static boolean isMagicDamage(DamageSource source) {
        return IronsCompat.isIronSpellDamage(source)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    /**
     * 是否为“普通攻击/直接命中”类物理伤害（排除魔法、火焰、爆炸、荆棘、铁魔法法术）。
     *
     * <p><b>铁魔法法术不计入物理：</b>铁魔法（Iron's Spellbooks）的法术伤害本质走物理口径
     * （吃护甲减免），但其 {@code DamageSource} 是模组自定义类型、不是原版
     * {@code DamageTypes.MAGIC}，若不显式排除就会被本判定当成物理伤害。按本模组规则，
     * 一切「由物理伤害触发」的效果都不应由铁魔法法术触发，故在此统一挡掉。</p>
     */
    private static boolean isBasicPhysical(DamageSource source) {
        // 铁魔法法术伤害：虽然本质是物理口径，但按本模组规则不计入「物理伤害触发」
        if (IronsCompat.isIronSpellDamage(source)) {
            return false;
        }
        // 1.20.1 的 DamageSource 没有 isMagic()/isFire()/isExplosion() 便捷方法，
        // 统一用伤害类型标签判定
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.LAVA) || source.is(DamageTypes.EXPLOSION)
                || source.is(DamageTypes.PLAYER_EXPLOSION)
                || source.is(DamageTypes.THORNS)) {
            return false;
        }
        return source.getDirectEntity() instanceof LivingEntity
                || source.getDirectEntity() instanceof Projectile
                || source.getEntity() instanceof LivingEntity;
    }

    private static boolean isEnemyOf(LivingEntity owner, LivingEntity target) {
        if (owner == null || target == null || owner == target || target.isDeadOrDying()) {
            return false;
        }
        if (target instanceof Player other) {
            if (owner.level().getServer() == null
                    || !owner.level().getServer().isPvpAllowed()) {
                return false;
            }
            if (owner.isSpectator() || other.isSpectator()) {
                return false;
            }
            // 同队/盟友（含自己阵营）不算敌人；队伍的“允许误伤”由玩家自行设置
            return !owner.isAlliedTo(other);
        }
        if (target instanceof Mob mob) {
            if (mob.isDeadOrDying()) {
                return false;
            }
            if (mob instanceof Enemy) {
                return true;
            }
            LivingEntity goal = mob.getTarget();
            return goal == owner || mob.getLastHurtByMob() == owner;
        }
        return false;
    }

    /**
     * 收集以 center 为圆心、半径 radius 内的全部敌对生物（owner 为判定发起方）。
     * 同包的主动技能事件（新月等）也复用它。
     */
    static List<LivingEntity> enemiesAround(LivingEntity center, LivingEntity owner, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        if (center.level().isClientSide) {
            return result;
        }
        AABB box = AABB.ofSize(center.position(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : center.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> isEnemyOf(owner, entity))) {
            result.add(e);
        }
        return result;
    }

    // ------------------------------------------------------------------ //
    // 内部状态
    // ------------------------------------------------------------------ //

    private static final class BurnState {
        private double dps;
        private int remainingTicks;
        /** 该灼烧的魔法学派（本模组魔法伤害 = 铁魔法学派伤害）。 */
        private String school;

        private BurnState(double dps, int remainingTicks, String school) {
            this.dps = dps;
            this.remainingTicks = remainingTicks;
            this.school = school;
        }
    }

    private static final class MadnessState {
        private int stacks;
        private int windowTicks;

        private MadnessState(int stacks) {
            this.stacks = stacks;
        }
    }

    private static final class ManaSurge {
        private final double total;
        private final int totalTicks;
        private int remainingTicks;

        private ManaSurge(double total, int totalTicks) {
            this.total = total;
            this.totalTicks = totalTicks;
            this.remainingTicks = totalTicks;
        }
    }
}
