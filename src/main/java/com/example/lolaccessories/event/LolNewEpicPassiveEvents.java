package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.combat.LolCritSystem;
import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.IronsMagicBridge;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.init.ModEntityTypeTags;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.LOLNetworking;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector3f;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
    /** 咒刃就绪过期：UUID → 就绪截止毫秒。 */
    private static final Map<UUID, Long> SPELLBLADE_EXPIRE_MS = new HashMap<>();
    /** 生命残片冷却：UUID → 下次可用毫秒。 */
    private static final Map<UUID, Long> LIFELINE_CD_MS = new HashMap<>();
    /** 法盾：UUID → 是否就绪（true=可格挡一次）。 */
    private static final Map<UUID, Boolean> ANNUL_READY = new HashMap<>();
    /** 女妖面纱与夜之锋刃共用法盾状态，但各自拥有主题一致的护罩渲染。 */
    private static final String GEAR_BANSHEES_VEIL = "banshees_veil";
    private static final String GEAR_EDGE_OF_NIGHT = "edge_of_night";
    /** 亡者的板甲：UUID → 沉船者动量状态。 */
    private static final Map<UUID, ShipwreckerState> SHIPWRECKER = new HashMap<>();
    /** 界弓：UUID → 5 秒攻击层数。 */
    private static final Map<UUID, TerminusState> TERMINUS = new HashMap<>();
    /** 法盾冷却：UUID → 下次就绪毫秒。 */
    private static final Map<UUID, Long> ANNUL_CD_MS = new HashMap<>();
    /** 启迪法力分流：UUID → 剩余状态。 */
    private static final Map<UUID, ManaSurge> MANA_SURGE = new HashMap<>();
    /** 升级检测：UUID → 上一秒看到的总经验等级。 */
    private static final Map<UUID, Integer> LAST_LEVEL = new HashMap<>();

    /** 鬼索的狂暴之刃：UUID → 攻速叠层状态。 */
    private static final Map<UUID, GuinsooState> GUINSOO = new HashMap<>();
    /** 鬼索的狂暴之刃：攻速叠层修饰符固定 UUID（transient，每次命中刷新）。 */
    private static final UUID GUINSOO_AS_UUID =
            UUID.fromString("a1b2c3d4-1111-4a5b-8c6d-000000000010");
    /** 鬼索的狂暴之刃：叠层蓄力速度修饰符固定 UUID（与攻速叠层等额，满足攻速必配等额蓄力速度口径）。 */
    private static final UUID GUINSOO_DS_UUID =
            UUID.fromString("a1b2c3d4-1113-4a5b-8c6d-000000000013");
    /** 蜕生：UUID → 死中新生下次可用毫秒。 */
    private static final Map<UUID, Long> CRYPTBLOOM_CD = new HashMap<>();
    /** 幽梦之灵·萦绕：UUID → 上次进入战斗的毫秒（脱战窗口判定）。 */
    private static final Map<UUID, Long> YOUMUU_COMBAT = new HashMap<>();
    /** 幽梦之灵·萦绕：脱战移速修饰符固定 UUID（transient）。 */
    private static final UUID YOUMUU_HAUNT_UUID =
            UUID.fromString("a1b2c3d4-2222-4a5b-8c6d-000000000020");
    /** 朔极之矛「专注意志」：玩家当前层数与失效计时。 */
    private static final Map<UUID, FocusedWillState> FOCUSED_WILL = new HashMap<>();
    /** 黯影阔剑「夜行者」的每位佩戴者独立 3 秒冷却。 */
    private static final Map<UUID, Long> NIGHTSTALKER_NEXT_MS = new HashMap<>();
    /** 破舰者「船长」：普攻命中层数与失效计时。 */
    private static final Map<UUID, SkipperState> SKIPPER = new HashMap<>();
    /** 自然之力「坚定」：受魔法伤害叠层状态。 */
    private static final Map<UUID, SteadfastState> STEADFAST = new HashMap<>();
    private static final UUID STEADFAST_MR_UUID = UUID.fromString("a1b2c3d4-3331-4a5b-8c6d-000000000031");
    private static final UUID STEADFAST_MS_UUID = UUID.fromString("a1b2c3d4-3332-4a5b-8c6d-000000000032");
    /** 视界专注「亢奋射击」：目标标记截止毫秒。 */
    private static final Map<UUID, Long> HYPERSHOT_MARKS = new HashMap<>();
    /** 风暴狂涌「风暴驭者」：目标累计伤害与延迟爆发。 */
    private static final Map<UUID, StormsurgeState> STORMSURGE = new HashMap<>();
    /** 裂隙制造者「虚空侵蚀」：每位佩戴者的层数与战斗计时（战斗中每秒叠 1 层）。 */
    private static final Map<UUID, RiftState> RIFT = new HashMap<>();
    private static final UUID RIFT_OMNIVAMP_UUID = UUID.fromString("a1b2c3d4-3333-4a5b-8c6d-000000000033");
    private static final UUID RIFT_INFUSION_UUID = UUID.fromString("a1b2c3d4-3334-4a5b-8c6d-000000000034");
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
        if (tick % 10 == 0) {
            updateFocusedWillAndSkipper(event.getServer());
            updateStormsurge(event.getServer());
            updateRiftmaker(event.getServer());
        }
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

    /** 每半秒维护朔极层数与破舰者船长叠层。 */
    private static void updateFocusedWillAndSkipper(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            FocusedWillState focus = FOCUSED_WILL.get(uuid);
            if (focus != null && (now - focus.lastDamageMs > focus.expireMs
                    || !CuriosGearWear.isWearing(player, "spear_of_shojin"))) {
                FOCUSED_WILL.remove(uuid);
            }

            SkipperState skipper = SKIPPER.get(uuid);
            if (skipper != null && (now > skipper.expireMs
                    || !CuriosGearWear.isWearing(player, "hullbreaker"))) {
                SKIPPER.remove(uuid);
            }

            TerminusState terminus = TERMINUS.get(uuid);
            if (terminus != null && (now > terminus.expireMs
                    || !CuriosGearWear.isWearing(player, "terminus"))) {
                clearTerminusDefenses(player);
                TERMINUS.remove(uuid);
            }

            SteadfastState steadfast = STEADFAST.get(uuid);
            if (steadfast != null && (now > steadfast.expireMs
                    || !CuriosGearWear.isWearing(player, "force_of_nature"))) {
                clearSteadfast(player);
                STEADFAST.remove(uuid);
            }
        }
    }

    private static void buildFocusedWill(ServerPlayer player, long now) {
        if (!CuriosGearWear.isWearing(player, "spear_of_shojin")) {
            return;
        }
        GearConfig config = GearConfigManager.get("spear_of_shojin");
        GearConfig.OnHitEffect effect = config == null ? null : config.findEffect("focused_will").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        FocusedWillState state = FOCUSED_WILL.computeIfAbsent(player.getUUID(), ignored -> new FocusedWillState());
        state.lastDamageMs = now;
        state.expireMs = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D) * 1000.0D);
        if (now - state.lastStackMs < 1000L) {
            return;
        }
        state.lastStackMs = now;
        state.stacks = Math.min(effect.max_stacks > 0 ? effect.max_stacks : 4, state.stacks + 1);
    }

    private static void applyFocusedWillDamageBonus(ServerPlayer player, LivingHurtEvent event) {
        FocusedWillState state = FOCUSED_WILL.get(player.getUUID());
        if (state == null || state.stacks <= 0) {
            return;
        }
        GearConfig config = GearConfigManager.get("spear_of_shojin");
        GearConfig.OnHitEffect effect = config == null ? null : config.findEffect("focused_will").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        double bonus = state.stacks * (effect.amount > 0 ? effect.amount : 0.03D);
        event.setAmount(event.getAmount() * (float) (1.0D + bonus));
    }

    /** 夜行者：隐身状态的普攻命中敌人时，造成 50 + 150% 固定护甲穿透的真实伤害。 */
    private static void handleNightstalker(Player wearer, LivingEntity victim,
                                           DamageSource source, GearConfig.OnHitEffect effect) {
        if (!wearer.isInvisible() || !isBasicPhysical(source) || !isEnemyOf(wearer, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID uuid = wearer.getUUID();
        if (now < NIGHTSTALKER_NEXT_MS.getOrDefault(uuid, 0L)) {
            return;
        }
        double cooldown = effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 3.0D;
        NIGHTSTALKER_NEXT_MS.put(uuid, now + Math.round(cooldown * 1000.0D));
        var armorPierceAttribute = BuiltInRegistries.ATTRIBUTE.get(
                new ResourceLocation("attributeslib", "armor_pierce"));
        double armorPierce = armorPierceAttribute == null ? 0.0D
                : wearer.getAttributeValue(armorPierceAttribute);
        double baseDamage = effect.base_damage > 0 ? effect.base_damage : 50.0D;
        double scale = effect.armor_pierce_scale > 0 ? effect.armor_pierce_scale : 1.5D;
        float trueDamage = (float) (baseDamage + armorPierce * scale);
        if (trueDamage > 0.0F) {
            victim.hurt(victim.level().damageSources().fellOutOfWorld(), trueDamage);
        }
    }

    /** 船长：普攻命中叠 1 层，满 5 层的本次命中附加近战口径物理伤害。 */
    private static void handleSkipper(Player wearer, LivingEntity victim,
                                      DamageSource source, LivingHurtEvent event,
                                      GearConfig.OnHitEffect effect) {
        if (!isBasicPhysical(source) || !isEnemyOf(wearer, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID uuid = wearer.getUUID();
        SkipperState state = SKIPPER.computeIfAbsent(uuid, ignored -> new SkipperState());
        int maxStacks = effect.max_stacks > 0 ? effect.max_stacks : 5;
        state.stacks++;
        state.expireMs = now + Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D) * 1000.0D);
        if (state.stacks < maxStacks) {
            return;
        }
        state.stacks = 0;
        double baseAttack = wearer.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double maxHealth = wearer.getMaxHealth();
        double adRatio = effect.ad_ratio > 0 ? effect.ad_ratio : 1.2D;
        double healthRatio = effect.max_health_pct > 0 ? effect.max_health_pct : 0.05D;
        event.setAmount(event.getAmount() + (float) (baseAttack * adRatio + maxHealth * healthRatio));
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
        // 幽梦·萦绕：任意“造成伤害/受到伤害”刷新脱战计时
        long combatNow = System.currentTimeMillis();
        if (victim instanceof Player pv) {
            YOUMUU_COMBAT.put(pv.getUUID(), combatNow);
        }
        if (attacker instanceof Player pa) {
            YOUMUU_COMBAT.put(pa.getUUID(), combatNow);
            if (pa instanceof ServerPlayer serverPlayer && !secondaryDamageDispatching
                    && original > 0.0F && isEnemyOf(pa, victim)) {
                applyFocusedWillDamageBonus(serverPlayer, event);
                buildFocusedWill(serverPlayer, combatNow);
            }
        }

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

    /**
     * 登录时清理「弹射物伤害 / 蓄力速度」上数值异常的修正器（绝对值 &gt; 10）。
     * 这两个百分比属性的正常加成均在 ±10 以内；更大的数值只可能来自旧版本遗留的
     * 持久化脏数据（会导致无佩戴时面板出现 +8000% 之类的异常加成），一律移除并记录日志。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        for (String attrId : new String[] {"attributeslib:arrow_damage", "attributeslib:draw_speed"}) {
            var attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                    .getValue(new net.minecraft.resources.ResourceLocation(attrId));
            if (attribute == null) {
                continue;
            }
            var instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : instance.getModifiers()) {
                if (Math.abs(mod.getAmount()) > 10.0D) {
                    LOLAccessories.LOGGER.warn("[属性净化] {} 移除 {} 上的异常修正器 {}（amount={}，operation={}）",
                            player.getName().getString(), attrId, mod.getName(), mod.getAmount(), mod.getOperation());
                    instance.removeModifier(mod);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        UUID uuid = player.getUUID();
        // 狂徒之心 + 狂徒之活力：每秒结算一次
        if (player.tickCount % 20 == 0) {
            warmogHeart(player);
            updateWarmogVigor(player);
            updateRabadonAmplify(player);
            clearExpiredSpellbladeAttackSpeed(player);
            updateFrostHeartAura(player);
            updateGuinsoo(player);
            updateYoumuuHaunt(player);
        }
        if (player.tickCount % 5 == 0) {
            updateShipwrecker(player);
        }
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
        if (player.tickCount % 20 == 0) {
            if (!Boolean.TRUE.equals(ANNUL_READY.get(uuid))) {
                Long cdUntil = ANNUL_CD_MS.get(uuid);
                if (cdUntil == null || System.currentTimeMillis() >= cdUntil) {
                    ANNUL_READY.put(uuid, Boolean.TRUE);
                    ANNUL_CD_MS.remove(uuid);
                }
            }
            // 女妖面纱保留紫色涡环；夜之锋刃改用败魔同款紫晶球壳。
            if (Boolean.TRUE.equals(ANNUL_READY.get(uuid))) {
                if (CuriosGearWear.isWearing(player, GEAR_BANSHEES_VEIL)) {
                    LOLNetworking.sendGearFx(player, FxKind.SHIELD_BANSHEE, true, 40);
                }
                if (CuriosGearWear.isWearing(player, GEAR_EDGE_OF_NIGHT)) {
                    LOLNetworking.sendGearFx(player, FxKind.SHIELD_ROOKERN, true, 40);
                }
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
        SPELLBLADE_EXPIRE_MS.remove(uuid);
        LIFELINE_CD_MS.remove(uuid);
        ANNUL_READY.remove(uuid);
        ANNUL_CD_MS.remove(uuid);
        setShipwreckerSpeed(event.getEntity(), 0.0D);
        clearTerminusDefenses(event.getEntity());
        MANA_SURGE.remove(uuid);
        LAST_LEVEL.remove(uuid);
        FOCUSED_WILL.remove(uuid);
        NIGHTSTALKER_NEXT_MS.remove(uuid);
        SKIPPER.remove(uuid);
        SHIPWRECKER.remove(uuid);
        TERMINUS.remove(uuid);
        STEADFAST.remove(uuid);
        HYPERSHOT_MARKS.entrySet().removeIf(e -> e.getKey().equals(uuid));
        STORMSURGE.entrySet().removeIf(e -> e.getValue().wearer.equals(uuid));
        RIFT.remove(uuid);
    }

    // ------------------------------------------------------------------ //
    // 受害者佩戴的装备被动
    // ------------------------------------------------------------------ //

    private static final UUID SHIPWRECKER_SPEED_UUID =
            UUID.fromString("1d58bb7a-a70a-4e8d-9fa4-7f0a2d8d924e");

    /** 亡者的板甲：移动累积动量；满层获得移速并留下红色拖尾，静止时快速衰减。 */
    private static void updateShipwrecker(Player player) {
        if (!CuriosGearWear.isWearing(player, "dead_mans_plate")) {
            setShipwreckerSpeed(player, 0.0D);
            SHIPWRECKER.remove(player.getUUID());
            return;
        }
        GearConfig config = GearConfigManager.get("dead_mans_plate");
        GearConfig.OnHitEffect effect = config == null ? null : config.findEffect("shipwrecker").orElse(null);
        if (effect == null || !effect.enabled) {
            setShipwreckerSpeed(player, 0.0D);
            return;
        }
        ShipwreckerState state = SHIPWRECKER.computeIfAbsent(player.getUUID(), ignored -> new ShipwreckerState());
        int cap = Math.max(1, effect.max_stacks);
        double moved = state.lastPosition == null ? 0.0D : player.position().distanceTo(state.lastPosition);
        state.lastPosition = player.position();
        if (moved > 0.03D && !player.isPassenger()) {
            // 官方节奏：每 0.25 秒移动时 +7 动量，约 3.75 秒满层。
            state.shipwreckerStacks = Math.min(cap, state.shipwreckerStacks
                    + Math.max(1, (int) Math.round(effect.count > 0 ? effect.count : 7.0D)));
        } else {
            state.shipwreckerStacks = Math.max(0, state.shipwreckerStacks - 2);
        }
        boolean fullMomentum = state.shipwreckerStacks >= cap;
        setShipwreckerSpeed(player, fullMomentum ? Math.max(0.0D, effect.amount) : 0.0D);
        if (fullMomentum && player.level() instanceof ServerLevel level) {
            var movement = player.getDeltaMovement();
            var trail = movement.lengthSqr() > 1.0E-4D
                    ? player.position().subtract(movement.normalize().scale(0.45D)) : player.position();
            level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.02F, 0.02F), 1.55F),
                    trail.x, trail.y + 0.16D, trail.z, 8, 0.16D, 0.06D, 0.16D, 0.0D);
        }
        TerminusState terminus = TERMINUS.get(player.getUUID());
        if (terminus != null && terminus.expireMs > 0L && System.currentTimeMillis() >= terminus.expireMs) {
            TERMINUS.remove(player.getUUID());
        }
    }

    private static void setShipwreckerSpeed(Player player, double ratio) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        if (attribute.getModifier(SHIPWRECKER_SPEED_UUID) != null) {
            attribute.removeModifier(SHIPWRECKER_SPEED_UUID);
        }
        if (ratio > 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(SHIPWRECKER_SPEED_UUID, "shipwrecker_momentum",
                    ratio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

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
                    // 自然之力：受到魔法伤害时叠加坚定
                    case "steadfast" -> handleSteadfast(wearer, attacker, source, effect);
                    // 裂隙制造者：受到敌方伤害同样视为保持战斗（虚空侵蚀叠层条件）
                    case "void_corruption" -> {
                        if (attacker != null && attacker != wearer && isEnemyOf(wearer, attacker)) {
                            RIFT.computeIfAbsent(wearer.getUUID(), k -> new RiftState()).lastCombatMs =
                                    System.currentTimeMillis();
                        }
                    }
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
            // 反弹伤害 = 魔法伤害，学派由配置 school 决定（荆棘之甲/棘刺背心：nature 自然学派）。
            // 荆棘之甲带护甲加成：伤害 = 固定值 + armor_ratio × 佩戴者护甲（原版口径：20 + 10% 额外护甲）
            double thornAmount = effect.amount
                    + (effect.armor_ratio > 0 ? effect.armor_ratio * wearer.getAttributeValue(Attributes.ARMOR) : 0.0D);
            IronsSpellDamage.apply(wearer, attacker, (float) thornAmount,
                    IronsSpellDamage.resolve(effect.school));
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
        // 法盾被打破：护罩特效熄灭（就绪后重新亮起）
        LOLNetworking.sendGearFx(wearer, FxKind.SHIELD_BANSHEE, false, 0);
    }

    // ------------------------------------------------------------------ //
    // 造成伤害方佩戴的装备被动
    // ------------------------------------------------------------------ //

    /**
     * 友善或被动生物判定：动物（含驯服）、水生生物、傀儡、村民、蝙蝠、悦灵。
     * 所有装备的效果不对这类生物生效（盛名/涌动的击杀触发除外）。
     */
    public static boolean isPassiveFriendly(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.animal.Animal
                || entity instanceof net.minecraft.world.entity.animal.WaterAnimal
                || entity instanceof net.minecraft.world.entity.animal.AbstractGolem
                || entity instanceof net.minecraft.world.entity.npc.AbstractVillager
                || entity instanceof net.minecraft.world.entity.ambient.AmbientCreature
                || entity instanceof net.minecraft.world.entity.animal.allay.Allay;
    }

    private static void handleAttackerSide(Player wearer, LivingEntity victim, DamageSource source,
                                           LivingHurtEvent event, float original) {
        // 所有装备的效果不对友善或被动生物生效
        if (isPassiveFriendly(victim)) {
            return;
        }
        // 本模组被动派发的二次伤害（顺劈溅射、荆棘反伤命中）不再触发攻击方被动，
        // 否则溅射命中其他生物会再次走进 handleAttackerSide 再次顺劈，递归放大
        if (secondaryDamageDispatching) {
            return;
        }
        // 残疫·憎恨之雾：终极技能施放后的 3 秒窗口内，魔法伤害命中敌人 → 其脚下生成恨雾
        if (wearer instanceof ServerPlayer sp && isMagicDamage(source) && isEnemyOf(wearer, victim)) {
            LolMalignanceEvents.onUltimateDamage(sp, victim);
        }
        // 蜕生·死中新生（致死伤害兜底）：本次伤害足以致死 + 穿戴蜕生 + victim 敌对 → 触发。
        // 原因：部分环境下 LivingDeathEvent 未可靠派发到本订阅，故在致死伤害路径上兜底，冷却去重。
        if (wearer instanceof ServerPlayer sp
                && CuriosGearWear.isWearing(wearer, "cryptbloom")
                && wearer != victim && isEnemyOf(wearer, victim)
                && victim.getHealth() <= original) {
            triggerLifeFromDeath(sp, victim);
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
                        case "titanic_cleave" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                titanicCleaveAoe(wearer, victim, effect);
                            }
                        }
                        case "terminus" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                terminusHit(wearer, victim, effect);
                            }
                        }
                        case "shipwrecker" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                shipwreckerHit(wearer, victim, event, effect);
                            }
                        }
                        case "immolate" -> burnNearby(wearer, effect);
                        // 疾行（三相之力 Quicken）：普攻命中后短暂加速
                        case "quicken" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                int dur = (int) Math.round(
                                        (effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 20.0D);
                                wearer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, dur, 0,
                                        true, false));
                            }
                        }
                        // 艾卡西亚之咬（纳什之牙）：普攻额外魔法伤害（15 + 15% 法强，攻击特效）
                        case "icathian_bite" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                var spAttr = wearer.getAttribute(
                                        net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                                                new net.minecraft.resources.ResourceLocation(
                                                        "irons_spellbooks", "spell_power")));
                                double ap = spAttr != null ? spAttr.getValue() : 0.0D;
                                float dmg = (float) ((effect.base_damage > 0 ? effect.base_damage : 15.0D)
                                        + ap * (effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.15D));
                                IronsSpellDamage.apply(wearer, victim, dmg,
                                        IronsSpellDamage.resolve(effect.school));
                            }
                        }
                        // 凝霜（瑞莱的冰晶节杖）：造成魔法伤害时减速目标（每目标 0.5 秒内置间隔）
                        case "rimefrost" -> {
                            if (isMagicDamage(source) && isEnemyOf(wearer, victim) && canProcRimefrost(victim)) {
                                int dur = (int) Math.round(
                                        (effect.duration_seconds > 0 ? effect.duration_seconds : 1.0D) * 20.0D);
                                // 原版缓慢：每级 15% → 30% 即 amplifier 1
                                int amp = Math.max(0, (int) Math.round(
                                        (effect.amount > 0 ? effect.amount : 0.30D) / 0.15D) - 1);
                                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, dur, amp,
                                        true, false));
                            }
                        }
                        // 风怒（卢安娜的飓风）：普攻命中后向附近其他敌人射出额外箭矢（65% 伤害）
                        case "winds_fury" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                runaanBolts(wearer, victim, effect);
                            }
                        }
                        // 智慧末刃：命中附加魔法伤害（学派化）
                        case "wit_end_hit" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                float dmg = (float) (effect.base_damage > 0 ? effect.base_damage : 45.0D);
                                IronsSpellDamage.apply(wearer, victim, dmg,
                                        IronsSpellDamage.resolve(effect.school));
                            }
                        }
                        case "madness" -> handleMadness(wearer, victim, event, effect);
                        case "hypershot" -> handleHypershot(wearer, victim, source, event, effect);
                        case "void_corruption" -> handleVoidCorruption(wearer, victim, event, effect);
                        case "cinderbloom" -> handleCinderbloom(wearer, victim, source, event, effect);
                        case "stormraider" -> handleStormraider(wearer, victim, event, effect);
                        case "nightstalker" -> handleNightstalker(wearer, victim, source, effect);
                        case "skipper" -> handleSkipper(wearer, victim, source, event, effect);
                        // 永恒·施法命中：仅“造成魔法伤害命中敌方”时视为施法成功并回血
                        case "eternity" -> {
                            if (isMagicDamage(source) && isEnemyOf(wearer, victim)) {
                                healOnCast(wearer, effect);
                            }
                        }
                        // 鬼索·愤怒：普攻附带固定魔法伤害（攻击特效）
                        case "wrath" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                applyGuinsooWrath(wearer, victim);
                            }
                        }
                        // 鬼索·汹涌打击：普攻叠攻速，满层后每第 3 次攻击额外再触发一次攻击特效
                        case "seething_strike" -> {
                            if (isBasicPhysical(source) && isEnemyOf(wearer, victim)) {
                                handleGuinsooSeething(wearer, victim, effect);
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

    /** 自然之力：受到魔法伤害时叠层；满层后获得额外魔抗与移速。 */
    private static void handleSteadfast(Player wearer, LivingEntity attacker, DamageSource source,
                                       GearConfig.OnHitEffect effect) {
        if (attacker == null || attacker == wearer || !isMagicDamage(source)) {
            return;
        }
        long now = System.currentTimeMillis();
        int max = Math.max(1, effect.max_stacks > 0 ? effect.max_stacks : 8);
        long durationMs = Math.round(Math.max(1.0D, effect.duration_seconds > 0 ? effect.duration_seconds : 7.0D) * 1000.0D);
        SteadfastState state = STEADFAST.computeIfAbsent(wearer.getUUID(), ignored -> new SteadfastState());
        state.stacks = Math.min(max, state.stacks + 1);
        state.expireMs = now + durationMs;
        if (state.stacks >= max) {
            setTransientAttribute(wearer, ModAttributes.LOL_MAGIC_RESIST.get(), STEADFAST_MR_UUID,
                    "force_of_nature_steadfast_mr", effect.amount > 0 ? effect.amount : 70.0D);
            setSteadfastSpeed(wearer, effect.bonus_pct > 0 ? effect.bonus_pct : 0.06D);
            // 满层坚韧特效：翠绿自然光环（7 秒窗口，受击持续刷新）
            LOLNetworking.sendGearFx(wearer, FxKind.STEADFAST_AURA, true, 140);
        }
    }

    private static void clearSteadfast(Player player) {
        setTransientAttribute(player, ModAttributes.LOL_MAGIC_RESIST.get(), STEADFAST_MR_UUID,
                "force_of_nature_steadfast_mr", 0.0D);
        setSteadfastSpeed(player, 0.0D);
        LOLNetworking.sendGearFx(player, FxKind.STEADFAST_AURA, false, 0);
    }

    private static void setSteadfastSpeed(Player player, double ratio) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        if (attr.getModifier(STEADFAST_MS_UUID) != null) {
            attr.removeModifier(STEADFAST_MS_UUID);
        }
        if (ratio > 0.0D) {
            attr.addTransientModifier(new AttributeModifier(STEADFAST_MS_UUID, "force_of_nature_steadfast_speed",
                    ratio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
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

    /** 成功施法后装填夺萃之镰的咒刃；由可选的铁魔法兼容订阅器调用。 */
    public static void armEssenceReaverSpellblade(ServerPlayer wearer) {
        if (!CuriosGearWear.isWearing(wearer, "essence_reaver")) {
            return;
        }
        GearConfig config = GearConfigManager.get("essence_reaver");
        GearConfig.OnHitEffect effect = config == null ? null : config.findEffect("spellblade").orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lockUntil = SPELLBLADE_LOCK_MS.get(wearer.getUUID());
        if (lockUntil == null || now >= lockUntil) {
            SPELLBLADE_READY.put(wearer.getUUID(), Boolean.TRUE);
            SPELLBLADE_EXPIRE_MS.put(wearer.getUUID(), now + Math.round(
                    Math.max(1.0D, effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D) * 1000.0D));
        }
    }

    private static void handleSpellblade(Player wearer, LivingEntity victim, DamageSource source,
                                         LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        UUID uuid = wearer.getUUID();
        if (isMagicDamage(source)) {
            // 非铁魔法环境仍保留原有的魔法命中装填兜底；夺萃同时可由成功施法直接装填。
            long now = System.currentTimeMillis();
            Long lockUntil = SPELLBLADE_LOCK_MS.get(uuid);
            if (lockUntil == null || now >= lockUntil) {
                SPELLBLADE_READY.put(uuid, Boolean.TRUE);
                SPELLBLADE_EXPIRE_MS.put(uuid, now + Math.round(
                        Math.max(1.0D, effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D) * 1000.0D));
            }
            return;
        }
        if (!isBasicPhysical(source)) {
            return;
        }
        if (!Boolean.TRUE.equals(SPELLBLADE_READY.get(uuid))) {
            return;
        }
        Long expireAt = SPELLBLADE_EXPIRE_MS.get(uuid);
        if (expireAt != null && System.currentTimeMillis() >= expireAt) {
            SPELLBLADE_READY.put(uuid, Boolean.FALSE);
            SPELLBLADE_EXPIRE_MS.remove(uuid);
            return;
        }
        // 多件咒刃（如耀光 + 三相之力）同时装备：一次触发同时结算全部咒刃伤害（叠加），
        // 冷却共享且相加——只占用一个冷却窗口，不重复结算。
        SPELLBLADE_READY.put(uuid, Boolean.FALSE);
        SPELLBLADE_EXPIRE_MS.remove(uuid);
        double[] agg = spellbladeAggregate(wearer);
        if (agg[0] > 0.0D) {
            event.setAmount(event.getAmount() + (float) agg[0]);
        }
        if (agg[1] > 0.0D) {
            // 魔法咒刃部分（巫妖之祸）：攻击伤害 75% + 法术强度 45% 的额外魔法伤害（邪术学派）
            IronsSpellDamage.apply(wearer, victim, (float) agg[1],
                    IronsSpellDamage.resolve("eldritch"));
        }
        if (agg[3] > 0.0D) {
            IronsMagicBridge.addMana(wearer, (float) agg[3]);
        }
        if (agg[2] > 0.0D) {
            SPELLBLADE_LOCK_MS.put(uuid,
                    System.currentTimeMillis() + Math.round(agg[2] * 1000.0D));
        }
    }

    /** 咒刃触发附赠的 50% 攻速（1.5 秒，到期由 {@link #tickSpellbladeAttackSpeed} 移除）。 */
    private static final Map<UUID, Long> SPELLBLADE_AS_EXPIRE = new HashMap<>();

    private static void addSpellbladeAttackSpeed(Player wearer) {
        var attr = wearer.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("spellblade_as:" + wearer.getUUID()).getBytes());
        if (attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
        attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id,
                "spellblade_attack_speed", 0.5D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        SPELLBLADE_AS_EXPIRE.put(wearer.getUUID(), System.currentTimeMillis() + 1500L);
    }

    /** 每秒清理该玩家过期的咒刃攻速加成（由玩家 tick 调用）。 */
    private static void clearExpiredSpellbladeAttackSpeed(Player player) {
        Long expire = SPELLBLADE_AS_EXPIRE.get(player.getUUID());
        if (expire != null && System.currentTimeMillis() >= expire) {
            var attr = player.getAttribute(Attributes.ATTACK_SPEED);
            UUID id = UUID.nameUUIDFromBytes(("spellblade_as:" + player.getUUID()).getBytes());
            if (attr != null && attr.getModifier(id) != null) {
                attr.removeModifier(id);
            }
            SPELLBLADE_AS_EXPIRE.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------ //
    // 冰霜之心·冬之抚慰（光环：周围敌人 -20% 攻速）
    // ------------------------------------------------------------------ //

    /** 光环挂载标记：敌人 UUID → 光环失效毫秒（2.5 秒未刷新自动脱落）。 */
    private static final Map<UUID, Long> FROSTHEART_AURA = new HashMap<>();

    /** 凝霜（瑞莱）内置间隔：目标 UUID → 下次可触发毫秒。 */
    private static final Map<UUID, Long> RIMEFROST_CD = new HashMap<>();

    private static boolean canProcRimefrost(LivingEntity victim) {
        long now = System.currentTimeMillis();
        Long until = RIMEFROST_CD.get(victim.getUUID());
        if (until != null && now < until) {
            return false;
        }
        RIMEFROST_CD.put(victim.getUUID(), now + 500L);
        return true;
    }

    /**
     * 冬之抚慰（冰霜之心）：只对「与自己进入战斗状态」（双向伤害记录 8 秒窗口，
     * 复用心之钢的交战口径）且在 3.5 格内的敌人施加 -20% 攻速；脱离交战 / 走出
     * 范围 / 摘下装备时光环立即脱落，不是常驻生效。
     */
    private static void updateFrostHeartAura(Player player) {
        long now = System.currentTimeMillis();
        UUID modifierId = UUID.nameUUIDFromBytes("frostheart_aura".getBytes());
        GearConfig.OnHitEffect effect = CuriosGearWear.isWearing(player, "frozen_heart")
                ? GearConfigManager.get("frozen_heart").findEffect("winters_caress").orElse(null) : null;
        boolean wearing = effect != null && effect.enabled;
        double radius = effect != null && effect.radius_blocks > 0 ? effect.radius_blocks : 3.5D;
        double amount = effect != null && effect.amount > 0 ? effect.amount : 0.20D;

        // 1) 摘除：已走出范围 / 脱离交战 / 佩戴者摘下装备的敌人——光环立即脱落
        java.util.Iterator<Map.Entry<UUID, Long>> it = FROSTHEART_AURA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            LivingEntity e = findLivingByUuid(player, entry.getKey());
            boolean keep = wearing && e != null && e.isAlive()
                    && HeartsteelEvents.isInCombatWith(player, e)
                    && e.distanceToSqr(player) <= radius * radius;
            if (!keep) {
                if (e != null) {
                    removeFrostheartModifier(e, modifierId);
                }
                it.remove();
            }
        }

        // 2) 施加：范围内 + 与自己交战中的敌对生物
        if (!wearing) {
            return;
        }
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && isEnemyOf(player, e)
                        && HeartsteelEvents.isInCombatWith(player, e))) {
            var attr = e.getAttribute(Attributes.ATTACK_SPEED);
            if (attr == null) {
                continue;
            }
            if (attr.getModifier(modifierId) != null) {
                attr.removeModifier(modifierId);
            }
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    modifierId, "frostheart_aura", -amount,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
            FROSTHEART_AURA.put(e.getUUID(), now + 2500L);
        }
    }

    /** 在在线玩家的世界中按 UUID 找 LivingEntity（找不到返回 null，transient 修饰符随卸载自然消失）。 */
    private static LivingEntity findLivingByUuid(Player any, UUID uuid) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() instanceof net.minecraft.server.level.ServerLevel level
                    && level.getEntity(uuid) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void removeFrostheartModifier(LivingEntity e, UUID modifierId) {
        var attr = e.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null && attr.getModifier(modifierId) != null) {
            attr.removeModifier(modifierId);
        }
    }

    /** 卢安娜的飓风：向受害者附近的其他敌人射出额外箭矢（各造成 65% 攻击伤害的近似弹射物伤害）。 */
    private static void runaanBolts(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        int count = effect.count > 0 ? effect.count : 2;
        double ratio = effect.amount > 0 ? effect.amount : 0.65D;
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 8.0D;
        if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        List<LivingEntity> others = new ArrayList<>(
                enemiesAround(victim, wearer, radius));
        others.remove(victim);
        others.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(victim)));
        if (others.size() > count) {
            others = others.subList(0, count);
        }
        if (others.isEmpty()) {
            return;
        }
        double ad = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = 3.0D; // 弹射初速（Arrow 伤害 ≈ baseDamage × 命中时速度）
        float boltDamage = (float) Math.max(1.0D, ad * ratio / speed);
        for (LivingEntity t : others) {
            net.minecraft.world.entity.projectile.Arrow bolt =
                    net.minecraft.world.entity.EntityType.ARROW.create(victim.level());
            if (bolt == null) {
                continue;
            }
            bolt.setOwner(wearer);
            double dx = t.getX() - victim.getX();
            double dy = (t.getY() + t.getBbHeight() * 0.6D) - (victim.getEyeY() - 0.2D);
            double dz = t.getZ() - victim.getZ();
            double len = Math.max(0.1D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            bolt.setBaseDamage(boltDamage);
            bolt.shoot(dx / len, dy / len + 0.08D, dz / len, (float) speed, 0.5F);
            victim.level().addFreshEntity(bolt);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    t.getX(), t.getEyeY(), t.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.1D);
        }
        wearer.level().playSound(null, victim.blockPosition(),
                net.minecraft.sounds.SoundEvents.ARROW_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS,
                0.8F, 1.2F);
    }

    // ------------------------------------------------------------------ //
    // 智慧末刃：偷取传说魔法抗性（叠加，超时失效）
    // ------------------------------------------------------------------ //

    /** 一次偷取状态（持目标引用以便过期时恢复魔抗）。 */
    private static final class WitSteal {
        final LivingEntity target;
        int stacks;
        long expire;

        WitSteal(LivingEntity target) {
            this.target = target;
        }
    }

    private static final Map<UUID, WitSteal> WIT_END_STEAL = new HashMap<>();

    private static UUID witStealId(LivingEntity target) {
        return UUID.nameUUIDFromBytes(("wit_end_steal:" + target.getUUID()).getBytes());
    }

    /** 命中时叠加偷取：目标传说魔抗 -steal×层数（至多 5 层），duration 内未再被命中则失效。 */
    private static void witEndSteal(LivingEntity target, double steal, double durationSeconds) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                target.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
        if (attr == null) {
            return;
        }
        long now = System.currentTimeMillis();
        WitSteal st = WIT_END_STEAL.get(target.getUUID());
        if (st == null || now >= st.expire) {
            st = new WitSteal(target);
            WIT_END_STEAL.put(target.getUUID(), st);
        }
        st.stacks = Math.min(5, st.stacks + 1);
        st.expire = now + Math.round(durationSeconds * 1000.0D);
        UUID id = witStealId(target);
        if (attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
        attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                id, "wit_end_steal", -steal * st.stacks,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    /** 每秒清理过期的智慧末刃偷取（把魔抗还给目标）。 */
    private static void tickWitEndSteal() {
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<UUID, WitSteal>> it = WIT_END_STEAL.entrySet().iterator();
        while (it.hasNext()) {
            WitSteal st = it.next().getValue();
            if (now >= st.expire || st.target.isRemoved() || st.target.isDeadOrDying()) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                        st.target.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
                UUID id = witStealId(st.target);
                if (attr != null && attr.getModifier(id) != null) {
                    attr.removeModifier(id);
                }
                it.remove();
            }
        }
    }

    /** 斩击特效：横扫粒子 + 挥砍音效（提亚马特/贪欲九头蛇的顺劈与新月共用）。 */
    static void slashFx(Player wearer, LivingEntity victim) {
        if (wearer.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.8D, victim.getZ(),
                    1, 0.1D, 0.1D, 0.1D, 0.0D);
            wearer.level().playSound(null, victim.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * 聚合穿戴的全部咒刃效果：返回 {@code {物理伤害总和, 魔法伤害总和, 冷却总和(秒), 回蓝总和}}。
     * 物理咒刃（耀光/三相/夺萃）按攻击力比例；魔法咒刃（巫妖之祸）按法术强度比例。
     */
    private static double[] spellbladeAggregate(Player wearer) {
        double[] out = {0.0D, 0.0D, 0.0D, 0.0D};
        double ad = wearer.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double baseAd = wearer.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double ap = spellPower(wearer);
        double critChance = critChance(wearer);
        CuriosGearWear.forEachEquippedGear(wearer, gear -> {
            GearConfig cfg = GearConfigManager.get(gear.getGearId());
            if (cfg == null) {
                return;
            }
            GearConfig.OnHitEffect e = cfg.findEffect("spellblade").orElse(null);
            if (e == null || !e.enabled) {
                return;
            }
            if (e.ap_power_ratio > 0) {
                // 魔法咒刃（巫妖之祸）：攻击伤害与法强比例全部并入额外魔法伤害
                out[1] += ad * e.power_ratio + ap * e.ap_power_ratio;
            } else {
                double physical;
                if ("essence_reaver".equals(gear.getGearId())) {
                    // 官方夺萃：125% 基础攻击力 + 0~50（随暴击率线性缩放），而非总攻击力。
                    physical = baseAd * (e.power_ratio > 0 ? e.power_ratio : 1.25D)
                            + Math.max(0.0D, e.base_damage) * Mth.clamp(critChance, 0.0D, 1.0D);
                    out[3] += physical * Math.max(0.0D, e.amount);
                } else {
                    physical = ad * (e.power_ratio > 0 ? e.power_ratio : 1.0D) + e.base_damage;
                }
                out[0] += physical;
            }
            out[2] += e.cooldown_seconds > 0 ? e.cooldown_seconds : 1.5D;
        });
        return out;
    }

    /**
     * 狂徒之心（狂徒铠甲）：饰品栏（Curios 全槽位，含其他模组饰品）提供的
     * 「装备生命值」总和 ≥ 阈值（warmog_heart.amount，默认 1500）时激活——
     * 脱战 6 秒后每秒回复 5% 最大生命值，并冒心形粒子。
     */
    private static void warmogHeart(Player player) {
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            GearConfig cfg = GearConfigManager.get(gear.getGearId());
            if (cfg == null) {
                return;
            }
            GearConfig.OnHitEffect effect = cfg.findEffect("warmog_heart").orElse(null);
            if (effect == null || !effect.enabled) {
                return;
            }
            if (player.getHealth() >= player.getMaxHealth()) {
                return;
            }
            // 脱战判定：duration_seconds 内受到生物伤害则视为战斗中（配置默认 6 秒）
            double delaySeconds = effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D;
            if (player.tickCount - player.getLastHurtByMobTimestamp() <= delaySeconds * 20.0D) {
                return;
            }
            double threshold = effect.amount > 0 ? effect.amount : 1500.0D;
            if (accessoryHealthBonus(player) < threshold) {
                return;
            }
            double pct = effect.base_damage > 0 ? effect.base_damage : 0.05D;
            player.heal((float) (player.getMaxHealth() * pct));
            // 回血特效：贴地柔和绿金光环（自绘，不遮挡视野；替代原版心形粒子）
            LOLNetworking.sendGearFx(player, FxKind.WARMOG_RESTORE, true, 40);
        });
    }

    /** 狂徒之活力 modifier 固定 UUID（transient，每秒重设）。 */
    private static final UUID WARMOG_VIGOR_UUID = UUID.fromString("3c9a2f47-6b1d-4e88-a5f0-92d4c7e10b36");
    /** 死亡之帽法术放大器 modifier 固定 UUID（transient，每秒重设）。 */
    private static final UUID RABADON_UUID = UUID.fromString("a7f3e9c2-4d58-4b06-9e1d-3c25b8a70f49");

    /**
     * 法术放大器（灭世者的死亡之帽）：法术强度提高 30%（乘区，MULTIPLY_TOTAL）。
     * 佩戴时每秒刷新；未佩戴时移除。
     */
    private static void updateRabadonAmplify(Player player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attr = player.getAttribute(
                net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                        new net.minecraft.resources.ResourceLocation("irons_spellbooks", "spell_power")));
        if (attr == null) {
            return; // 铁魔法未安装
        }
        boolean wearing = false;
        boolean[] found = {false};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            if ("rabadons_deathcap".equals(gear.getGearId())) {
                found[0] = true;
            }
        });
        wearing = found[0];
        GearConfig cfg = GearConfigManager.get("rabadons_deathcap");
        GearConfig.OnHitEffect effect = cfg != null ? cfg.findEffect("spell_amplify").orElse(null) : null;
        double ratio = effect != null && effect.amount > 0 ? effect.amount : 0.30D;
        if (attr.getModifier(RABADON_UUID) != null) {
            attr.removeModifier(RABADON_UUID);
        }
        if (wearing) {
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    RABADON_UUID, "rabadon_amplify", ratio,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * 狂徒之活力（狂徒铠甲）：获得额外生命值 = 12% 装备生命值（饰品栏生命加成总和）。
     * 实时跟随统计值变化；未佩戴时移除。
     */
    private static void updateWarmogVigor(Player player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        boolean[] found = {false};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            if ("warmogs_armor".equals(gear.getGearId())) {
                found[0] = true;
            }
        });
        double vigor = 0.0D;
        if (found[0]) {
            GearConfig cfg = GearConfigManager.get("warmogs_armor");
            GearConfig.OnHitEffect effect = cfg != null ? cfg.findEffect("warmog_vigor").orElse(null) : null;
            double ratio = effect != null && effect.amount > 0 ? effect.amount : 0.12D;
            vigor = ratio * accessoryHealthBonus(player);
        }
        if (attr.getModifier(WARMOG_VIGOR_UUID) != null) {
            attr.removeModifier(WARMOG_VIGOR_UUID);
        }
        if (vigor > 0.0D) {
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    WARMOG_VIGOR_UUID, "warmog_vigor", vigor,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 「装备生命值」：饰品栏全部饰品（含其他模组饰品）给玩家 {@code max_health}
     * 挂上的 ADDITION 修饰符总和（基础生命 20 不计入；药水/心之钢涨血等
     * 同样挂在 max_health 上，会被一并统计）。
     */
    private static double accessoryHealthBonus(Player player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (net.minecraft.world.entity.ai.attributes.AttributeModifier m : attr.getModifiers()) {
            // 排除本模组动态生命来源（狂徒之活力/心之钢涨血），避免正反馈自我叠加
            if (m.getId().equals(WARMOG_VIGOR_UUID) || m.getId().equals(HeartsteelEvents.HEALTH_BONUS_UUID)) {
                continue;
            }
            if (m.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                sum += m.getAmount();
            }
        }
        return sum;
    }

    /** 界弓·交相：普攻交替叠加光/暗两种三层状态，并附加魔法伤害。 */
    private static void terminusHit(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        TerminusState state = TERMINUS.computeIfAbsent(wearer.getUUID(), ignored -> new TerminusState());
        int cap = Math.max(1, effect.max_stacks);
        if (state.nextLight) {
            state.lightStacks = Math.min(cap, state.lightStacks + 1);
        } else {
            state.darkStacks = Math.min(cap, state.darkStacks + 1);
        }
        state.nextLight = !state.nextLight;
        state.expireMs = System.currentTimeMillis() + Math.round(Math.max(1.0D, effect.duration_seconds) * 1000.0D);
        refreshTerminusDefenses(wearer, state, cap);
        double base = effect.base_damage > 0.0D ? effect.base_damage : 30.0D;
        double bonusAd = Math.max(0.0D, wearer.getAttributeValue(Attributes.ATTACK_DAMAGE)
                - wearer.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
        double ap = spellPower(wearer);
        double damage = base + bonusAd * Math.max(0.0D, effect.power_ratio)
                + ap * Math.max(0.0D, effect.ap_power_ratio);
        boolean was = secondaryDamageDispatching;
        secondaryDamageDispatching = true;
        try {
            IronsSpellDamage.apply(wearer, victim, (float) damage, IronsSpellDamage.resolve(effect.school));
        } finally {
            secondaryDamageDispatching = was;
        }
    }

    private static final UUID TERMINUS_ARMOR_UUID = UUID.fromString("8d4f3c81-5a7c-4c58-b915-7167fc4d9b61");
    private static final UUID TERMINUS_MAGIC_RESIST_UUID = UUID.fromString("e6502ff6-cc89-4a07-94ff-0105d968d186");
    private static final UUID TERMINUS_ARMOR_PEN_UUID = UUID.fromString("a2e2c795-2a77-49e3-a332-3fbd2d15651f");
    private static final UUID TERMINUS_MAGIC_PEN_UUID = UUID.fromString("d765b281-5e0a-4dfd-ac8c-5a1328b91e8e");

    /** 交相：光层给予护甲/魔抗，暗层给予护甲穿透/法术穿透。 */
    private static void refreshTerminusDefenses(Player player, TerminusState state, int cap) {
        int light = Math.min(cap, state.lightStacks);
        int dark = Math.min(cap, state.darkStacks);
        double resistPerStack = terminusResistPerStack(player);
        var armorShred = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getValue(new ResourceLocation("attributeslib", "armor_shred"));
        setTransientAttribute(player, Attributes.ARMOR, TERMINUS_ARMOR_UUID, "terminus_light_armor",
                light * resistPerStack);
        setTransientAttribute(player, ModAttributes.LOL_MAGIC_RESIST.get(), TERMINUS_MAGIC_RESIST_UUID,
                "terminus_light_magic_resist", light * resistPerStack);
        setTransientAttribute(player, armorShred, TERMINUS_ARMOR_PEN_UUID, "terminus_dark_armor_pen",
                dark * 0.10D);
        setTransientAttribute(player, ModAttributes.LOL_MAGIC_PEN_PERCENT.get(), TERMINUS_MAGIC_PEN_UUID,
                "terminus_dark_magic_pen", dark * 0.10D);
    }

    private static double terminusResistPerStack(Player player) {
        int level = Math.max(1, Math.min(18, player.experienceLevel));
        if (level >= 14) {
            return 8.0D;
        }
        return level >= 10 ? 7.0D : 6.0D;
    }

    private static void clearTerminusDefenses(Player player) {
        var armorShred = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getValue(new ResourceLocation("attributeslib", "armor_shred"));
        setTransientAttribute(player, Attributes.ARMOR, TERMINUS_ARMOR_UUID, "terminus_light_armor", 0.0D);
        setTransientAttribute(player, ModAttributes.LOL_MAGIC_RESIST.get(), TERMINUS_MAGIC_RESIST_UUID,
                "terminus_light_magic_resist", 0.0D);
        setTransientAttribute(player, armorShred, TERMINUS_ARMOR_PEN_UUID, "terminus_dark_armor_pen", 0.0D);
        setTransientAttribute(player, ModAttributes.LOL_MAGIC_PEN_PERCENT.get(), TERMINUS_MAGIC_PEN_UUID,
                "terminus_dark_magic_pen", 0.0D);
    }

    private static void setTransientAttribute(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                              UUID id, String name, double ratio) {
        if (attribute == null) {
            return;
        }
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
        if (ratio > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, name, ratio, AttributeModifier.Operation.ADDITION));
        }
    }

    /** 沉船者：普攻按当前动量比例造成至多 100% 攻击力 + 40 的额外物理伤害并消耗动量。 */
    private static void shipwreckerHit(Player wearer, LivingEntity victim, LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        ShipwreckerState state = SHIPWRECKER.get(wearer.getUUID());
        int cap = Math.max(1, effect.max_stacks);
        if (state == null || state.shipwreckerStacks <= 0) {
            return;
        }
        double scale = Mth.clamp(state.shipwreckerStacks / (double) cap, 0.0D, 1.0D);
        double bonus = ((effect.base_damage > 0.0D ? effect.base_damage : 40.0D)
                + wearer.getAttributeValue(Attributes.ATTACK_DAMAGE) * Math.max(0.0D, effect.power_ratio)) * scale;
        event.setAmount(event.getAmount() + (float) bonus);
        state.shipwreckerStacks = 0;
        state.lastPosition = wearer.position();
        setShipwreckerSpeed(wearer, 0.0D);
    }

    /** 巨型九头蛇：顺劈对目标与周围敌人造成 1% 最大生命；刚斩使下一击改为 4% 最大生命。 */
    private static void titanicCleaveAoe(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        slashFx(wearer, victim);
        boolean crescent = wearer instanceof ServerPlayer serverPlayer
                && LolNewActiveSkillEvents.consumeTitanicCrescent(serverPlayer);
        GearConfig.OnHitEffect crescentEffect = crescent
                ? GearConfigManager.get("titanic_hydra").findEffect("crescent").orElse(null) : null;
        // 顺劈：主目标 1%、其他目标 3%；刚斩：主目标 4%、其他目标 9%。全部取佩戴者最大生命值。
        double mainRatio = crescent && crescentEffect != null
                ? Math.max(0.0D, crescentEffect.base_damage) : Math.max(0.0D, effect.base_damage);
        double splashRatio = crescent && crescentEffect != null
                ? Math.max(0.0D, crescentEffect.max_health_pct) : Math.max(0.0D, effect.max_health_pct);
        double mainDamage = wearer.getMaxHealth() * mainRatio;
        double radius = effect.radius_blocks > 0.0D ? effect.radius_blocks : 2.5D;
        DamageSource physical = wearer.level().damageSources().playerAttack(wearer);
        boolean was = secondaryDamageDispatching;
        secondaryDamageDispatching = true;
        try {
            victim.hurt(physical, (float) Math.max(1.0D, mainDamage));
            for (LivingEntity enemy : enemiesAround(victim, wearer, radius)) {
                if (enemy != victim) {
                    enemy.hurt(physical, (float) Math.max(1.0D, wearer.getMaxHealth() * splashRatio));
                }
            }
        } finally {
            secondaryDamageDispatching = was;
        }
    }

    /** 顺劈：物理攻击命中目标后，对其周围敌对生物造成佩戴者攻击力×比例 的物理溅射（带斩击特效）。 */
    private static void cleaveAoe(Player wearer, LivingEntity victim, GearConfig.OnHitEffect effect) {
        slashFx(wearer, victim);
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

    /** 视界专注：远距离魔法命中标记目标；标记期间佩戴者对其增伤。 */
    private static void handleHypershot(Player wearer, LivingEntity victim, DamageSource source,
                                        LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        if (!isMagicDamage(source) || !isEnemyOf(wearer, victim)) {
            return;
        }
        UUID targetId = victim.getUUID();
        long now = System.currentTimeMillis();
        Long markUntil = HYPERSHOT_MARKS.get(targetId);
        if (markUntil != null && now < markUntil) {
            event.setAmount(event.getAmount() * (float) (1.0D + (effect.amount > 0 ? effect.amount : 0.10D)));
        }
        double minDistance = effect.radius_blocks > 0 ? effect.radius_blocks : 70.0D;
        if (wearer.distanceTo(victim) >= minDistance) {
            long durationMs = Math.round(Math.max(1.0D, effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D) * 1000.0D);
            HYPERSHOT_MARKS.put(targetId, now + durationMs);
        }
        if (HYPERSHOT_MARKS.size() > 256) {
            HYPERSHOT_MARKS.entrySet().removeIf(e -> now >= e.getValue());
        }
    }

    /** 裂隙制造者：命中敌人时刷新战斗计时；增伤按当前虚空侵蚀层数结算（叠层由 tick 驱动）。 */
    private static void handleVoidCorruption(Player wearer, LivingEntity victim, LivingHurtEvent event,
                                             GearConfig.OnHitEffect effect) {
        if (!isEnemyOf(wearer, victim)) {
            return;
        }
        RiftState state = RIFT.computeIfAbsent(wearer.getUUID(), k -> new RiftState());
        state.lastCombatMs = System.currentTimeMillis();
        double perStack = effect.per_stack > 0 ? effect.per_stack : 0.02D;
        if (state.stacks > 0 && !event.isCanceled()) {
            event.setAmount(event.getAmount() * (float) (1.0D + state.stacks * perStack));
        }
    }

    /**
     * 每 0.5 秒维护裂隙制造者（LoL 口径）：
     * <ul>
     * <li>虚空侵蚀：与敌方作战（近期造成或承受伤害）时每过 1 秒叠 1 层（每层 +2% 伤害，至多 4 层）；
     *     脱战后未满层 3 秒、满层 5 秒消退；叠满时获得全能吸血（近战 10% / 远程 6%）。</li>
     * <li>虚空灌注：获得相当于 2% 额外生命值（最大生命值 − 原版基础 20）的法术强度。</li>
     * </ul>
     */
    private static void updateRiftmaker(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            GearConfig config = CuriosGearWear.isWearing(player, "riftmaker")
                    ? GearConfigManager.get("riftmaker") : null;
            GearConfig.OnHitEffect corruption = config == null
                    ? null : config.findEffect("void_corruption").orElse(null);
            if (corruption == null || !corruption.enabled) {
                if (RIFT.remove(uuid) != null) {
                    clearRiftmakerModifiers(player);
                }
                continue;
            }
            RiftState state = RIFT.computeIfAbsent(uuid, k -> new RiftState());
            int max = Math.max(1, corruption.max_stacks > 0 ? corruption.max_stacks : 4);
            long combatWindowMs = Math.round(Math.max(1.0D,
                    corruption.duration_seconds > 0 ? corruption.duration_seconds : 3.0D) * 1000.0D);
            boolean inCombat = now - state.lastCombatMs <= combatWindowMs;
            if (!inCombat) {
                // 脱战：层数消退（满层 5 秒、未满层 3 秒后才清零——这里窗口本身就是战斗计时，
                // 超窗即视为脱战足够久，直接清零；满层宽限按 5 秒另行放宽）
                long graceMs = state.stacks >= max
                        ? Math.round(5.0D * 1000.0D) : combatWindowMs;
                if (now - state.lastCombatMs > graceMs) {
                    state.stacks = 0;
                }
            } else if (state.stacks < max) {
                long intervalMs = Math.round(Math.max(0.25D,
                        corruption.interval_seconds > 0 ? corruption.interval_seconds : 1.0D) * 1000.0D);
                if (now - state.lastStackMs >= intervalMs) {
                    state.stacks++;
                    state.lastStackMs = now;
                }
            }
            // 满层全能吸血：近战 amount / 远程 bonus_pct（远程判定 = 主手弹射物武器）
            boolean full = state.stacks >= max;
            double omnivamp = 0.0D;
            if (full) {
                omnivamp = isRangedWeapon(player)
                        ? (corruption.bonus_pct > 0 ? corruption.bonus_pct : 0.06D)
                        : (corruption.amount > 0 ? corruption.amount : 0.10D);
            }
            setTransientAttribute(player, ModAttributes.LOL_OMNIVAMP.get(), RIFT_OMNIVAMP_UUID,
                    "riftmaker_omnivamp", omnivamp);
            // 虚空灌注：+2% 额外生命值的法术强度（基于静态最大生命值单次重算，无闭环）
            GearConfig.OnHitEffect infusion = config.findEffect("void_infusion").orElse(null);
            double infusionAp = infusion != null && infusion.enabled && infusion.bonus_pct > 0.0D
                    ? infusion.bonus_pct * Math.max(0.0D, player.getMaxHealth() - 20.0D) : 0.0D;
            var spellPowerAttr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation("irons_spellbooks", "spell_power"));
            setTransientAttribute(player, spellPowerAttr, RIFT_INFUSION_UUID,
                    "riftmaker_void_infusion", infusionAp);
        }
    }

    /** 摘下裂隙制造者/登出时清除满层全能吸血与虚空灌注定时属性。 */
    private static void clearRiftmakerModifiers(Player player) {
        setTransientAttribute(player, ModAttributes.LOL_OMNIVAMP.get(), RIFT_OMNIVAMP_UUID,
                "riftmaker_omnivamp", 0.0D);
        var spellPowerAttr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("irons_spellbooks", "spell_power"));
        setTransientAttribute(player, spellPowerAttr, RIFT_INFUSION_UUID,
                "riftmaker_void_infusion", 0.0D);
    }

    /** LoL 近战/远程口径的近似判定：主手为弹射物武器（弓/弩/三叉戟除外）视为远程。 */
    private static boolean isRangedWeapon(Player player) {
        return player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
    }

    /** 影焰：对低生命值目标造成的魔法伤害提高。 */
    private static void handleCinderbloom(Player wearer, LivingEntity victim, DamageSource source,
                                          LivingHurtEvent event, GearConfig.OnHitEffect effect) {
        if (!isMagicDamage(source) || !isEnemyOf(wearer, victim)) {
            return;
        }
        double threshold = effect.max_health_pct > 0 ? effect.max_health_pct : 0.40D;
        if (victim.getHealth() / Math.max(1.0F, victim.getMaxHealth()) <= threshold) {
            event.setAmount(event.getAmount() * (float) (1.0D + (effect.amount > 0 ? effect.amount : 0.20D)));
        }
    }

    /** 风暴狂涌：短时间内累计高额伤害后延迟引爆雷电伤害。 */
    private static void handleStormraider(Player wearer, LivingEntity victim, LivingHurtEvent event,
                                          GearConfig.OnHitEffect effect) {
        if (!isEnemyOf(wearer, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID targetId = victim.getUUID();
        StormsurgeState state = STORMSURGE.get(targetId);
        long cooldownMs = Math.round(Math.max(1.0D, effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 30.0D) * 1000.0D);
        if (state != null && state.cooldownUntilMs > now) {
            return;
        }
        long windowMs = Math.round(Math.max(0.5D, effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 1000.0D);
        if (state == null || !state.wearer.equals(wearer.getUUID()) || now > state.windowUntilMs) {
            state = new StormsurgeState(wearer.getUUID(), victim, now + windowMs);
            STORMSURGE.put(targetId, state);
        }
        state.damage += Math.max(0.0F, event.getAmount());
        double threshold = Math.max(1.0F, victim.getMaxHealth()) * (effect.max_health_pct > 0 ? effect.max_health_pct : 0.25D);
        if (!state.armed && state.damage >= threshold) {
            state.armed = true;
            state.explodeAtMs = now + windowMs;
            state.cooldownUntilMs = now + cooldownMs;
            state.baseDamage = effect.base_damage > 0 ? effect.base_damage : 125.0D;
            state.apRatio = effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.10D;
            state.school = effect.school;
            // 骤风标记特效：目标周身紫金电弧攒聚（延迟引爆窗口，2 秒）
            LOLNetworking.sendGearFx(victim, FxKind.STORMSURGE_MARK, true,
                    (int) Math.max(1, Math.round(windowMs / 50.0D)));
        }
    }

    private static void updateStormsurge(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<UUID> remove = new ArrayList<>();
        for (Map.Entry<UUID, StormsurgeState> entry : STORMSURGE.entrySet()) {
            StormsurgeState state = entry.getValue();
            if ((!state.armed && now > state.windowUntilMs) || state.target.isRemoved() || !state.target.isAlive()) {
                remove.add(entry.getKey());
                continue;
            }
            if (state.armed && now >= state.explodeAtMs) {
                ServerPlayer wearer = server.getPlayerList().getPlayer(state.wearer);
                if (wearer != null && wearer.isAlive() && CuriosGearWear.isWearing(wearer, "stormsurge")
                        && isEnemyOf(wearer, state.target)) {
                    boolean was = secondaryDamageDispatching;
                    secondaryDamageDispatching = true;
                    try {
                        float damage = (float) (state.baseDamage + spellPower(wearer) * state.apRatio);
                        IronsSpellDamage.apply(wearer, state.target, damage, IronsSpellDamage.resolve(state.school));
                    } finally {
                        secondaryDamageDispatching = was;
                    }
                    wearer.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                            30, 0, true, false));
                    // 熄灭骤风标记
                    LOLNetworking.sendGearFx(state.target, FxKind.STORMSURGE_MARK, false, 0);
                    // 劈击视觉：原版闪电视觉实体（不点燃、不伤害），配合惊雷声
                    net.minecraft.world.entity.LightningBolt bolt =
                            net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(state.target.level());
                    if (bolt != null) {
                        bolt.moveTo(state.target.getX(), state.target.getY(), state.target.getZ());
                        bolt.setVisualOnly(true);
                        state.target.level().addFreshEntity(bolt);
                    }
                }
                remove.add(entry.getKey());
            }
        }
        remove.forEach(STORMSURGE::remove);
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
        if (player.getAbsorptionAmount() > 0.0F || ShieldHpService.hasActive(uuid)) {
            return;
        }
        double base = effect.shield_amount > 0 ? effect.shield_amount : 150.0D;
        double shield = base * (1.0D + healPowerBonus(player));
        double seconds = effect.duration_seconds > 0 ? effect.duration_seconds : 2.5D;
        // 真护盾（白盾）：不经原版吸收，由 ShieldBarHud 绘制护盾条
        if (player instanceof ServerPlayer serverPlayer) {
            ShieldHpService.apply(serverPlayer, ShieldHpService.SOURCE_LIFELINE,
                    (float) shield, Math.round(seconds * 1000.0D), ShieldType.WHITE);
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

    static boolean isMagicDamage(DamageSource source) {
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

    public static boolean isEnemyOf(LivingEntity owner, LivingEntity target) {
        if (owner == null || target == null || owner == target || target.isDeadOrDying()) {
            return false;
        }
        // 「玩家替身」标签的测试假人按玩家语义判定（队友/盟友规则），但不套用 PvP 开关与观战限制
        boolean playerLike = target instanceof Player
                || target.getType().is(ModEntityTypeTags.PLAYER_LIKE);
        if (playerLike) {
            if (target instanceof Player other) {
                if (owner.level().getServer() == null
                        || !owner.level().getServer().isPvpAllowed()) {
                    return false;
                }
                if (owner.isSpectator() || other.isSpectator()) {
                    return false;
                }
            }
            // 同队/盟友（含自己阵营）不算敌人；队伍的“允许误伤”由玩家自行设置
            return !owner.isAlliedTo(target);
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
    // 鬼索的狂暴之刃 / 蜕生 / 幽梦之灵 专属逻辑与状态
    // ------------------------------------------------------------------ //

    /** 鬼索的狂暴之刃·攻速叠层状态。 */
    private static final class GuinsooState {
        int stacks = 0;
        long expire = 0L;
        int attackCounter = 0;
    }

    /** 鬼索·愤怒：普攻附带的固定魔法伤害（攻击特效）。数据来自 guinsoos_rageblade.json 的 wrath 条目。 */
    private static void applyGuinsooWrath(LivingEntity wearer, LivingEntity victim) {
        GearConfig.OnHitEffect effect = GearConfigManager.get("guinsoos_rageblade")
                .findEffect("wrath").orElse(null);
        if (effect == null) {
            return;
        }
        double ap = spellPower(wearer);
        float base = (float) (effect.base_damage + ap * (effect.ap_power_ratio > 0
                ? effect.ap_power_ratio : 0.0D));
        IronsSpellDamage.apply(wearer, victim, base, IronsSpellDamage.resolve(effect.school));
    }

    /** 鬼索·汹涌打击：普攻叠攻速（最多 4 层，4 秒），满层后每第 3 次攻击额外再触发一次攻击特效。 */
    private static void handleGuinsooSeething(LivingEntity wearer, LivingEntity victim,
                                              GearConfig.OnHitEffect effect) {
        UUID uuid = wearer.getUUID();
        GuinsooState st = GUINSOO.computeIfAbsent(uuid, k -> new GuinsooState());
        int max = effect.max_stacks > 0 ? effect.max_stacks : 4;
        double per = effect.per_stack > 0 ? effect.per_stack : 0.08D;
        st.stacks = Math.min(max, st.stacks + 1);
        st.expire = System.currentTimeMillis()
                + (long) ((effect.duration_seconds > 0 ? effect.duration_seconds : 4.0D) * 1000.0D);
        var attr = wearer.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null) {
            if (attr.getModifier(GUINSOO_AS_UUID) != null) {
                attr.removeModifier(GUINSOO_AS_UUID);
            }
            attr.addTransientModifier(new AttributeModifier(GUINSOO_AS_UUID, "guinsoo_as",
                    st.stacks * per, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        // 攻速叠层必须配等额蓄力速度（attributeslib:draw_speed，依赖 Apothic Attributes）
        var dsAttr = wearer.getAttribute(drawSpeedAttr());
        if (dsAttr != null) {
            if (dsAttr.getModifier(GUINSOO_DS_UUID) != null) {
                dsAttr.removeModifier(GUINSOO_DS_UUID);
            }
            dsAttr.addTransientModifier(new AttributeModifier(GUINSOO_DS_UUID, "guinsoo_draw",
                    st.stacks * per, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (st.stacks >= max) {
            st.attackCounter++;
            if (st.attackCounter % 3 == 0) {
                applyGuinsooWrath(wearer, victim);   // 满层每第 3 次攻击额外再触发一次攻击特效
            }
        }
    }

    /** 每秒清理过期的鬼索攻速叠层（由玩家 tick 调用）。 */
    private static void updateGuinsoo(Player player) {
        GuinsooState st = GUINSOO.get(player.getUUID());
        if (st != null && System.currentTimeMillis() > st.expire) {
            var attr = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attr != null && attr.getModifier(GUINSOO_AS_UUID) != null) {
                attr.removeModifier(GUINSOO_AS_UUID);
            }
            var dsAttr = player.getAttribute(drawSpeedAttr());
            if (dsAttr != null && dsAttr.getModifier(GUINSOO_DS_UUID) != null) {
                dsAttr.removeModifier(GUINSOO_DS_UUID);
            }
            GUINSOO.remove(player.getUUID());
        }
    }

    /** 幽梦之灵·萦绕：脱战（3 秒未造成伤害/受伤）后获得 +6% 移速；战斗状态立即失效。 */
    private static void updateYoumuuHaunt(Player player) {
        boolean wearing = CuriosGearWear.isWearing(player, "youmuus_ghostblade");
        GearConfig.OnHitEffect effect = wearing
                ? GearConfigManager.get("youmuus_ghostblade").findEffect("haunt").orElse(null)
                : null;
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        boolean active = effect != null && effect.enabled;
        long now = System.currentTimeMillis();
        Long last = YOUMUU_COMBAT.get(player.getUUID());
        double window = effect != null && effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D;
        boolean outOfCombat = last == null || now - last > window * 1000.0D;
        if (active && outOfCombat) {
            double ratio = effect.amount > 0 ? effect.amount : 0.06D;
            if (attr.getModifier(YOUMUU_HAUNT_UUID) != null) {
                attr.removeModifier(YOUMUU_HAUNT_UUID);
            }
            attr.addTransientModifier(new AttributeModifier(YOUMUU_HAUNT_UUID, "youmuu_haunt",
                    ratio, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (attr.getModifier(YOUMUU_HAUNT_UUID) != null) {
            attr.removeModifier(YOUMUU_HAUNT_UUID);
        }
    }

    /** 读取铁魔法的法术强度属性值。 */
    private static double spellPower(LivingEntity e) {
        var attr = e.getAttribute(net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "spell_power")));
        return attr != null ? attr.getValue() : 0.0D;
    }

    /** 读取本模组暴击率，夺萃之镰咒刃用其线性换算 0~50 额外伤害。 */
    private static double critChance(LivingEntity e) {
        var attr = e.getAttribute(ModAttributes.LOL_CRIT_CHANCE.get());
        return attr != null ? attr.getValue() : 0.0D;
    }

    /** 读取 Apothic Attributes 的蓄力速度属性（attributeslib:draw_speed），未安装则返回 null。 */
    private static net.minecraft.world.entity.ai.attributes.Attribute drawSpeedAttr() {
        return net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getValue(new net.minecraft.resources.ResourceLocation("attributeslib", "draw_speed"));
    }

    /**
     * 蜕生·死中新生：佩戴者击杀敌人后，于其死亡位置爆发治疗新星（绿色大光球 + 展开回血法阵），
     * 治疗自身与 4 格内友方玩家 100 + 20% 法强，冷却 60 秒。数据来自 cryptbloom.json 的 life_from_death 条目。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LOLAccessories.LOGGER.info("[蜕生诊断] LivingDeathEvent: victim={}({}), source={}",
                victim.getName().getString(), victim.getType(),
                event.getSource() != null ? event.getSource().getMsgId() : "null");
        var source = event.getSource();
        if (source == null) {
            return;
        }
        Entity srcEntity = source.getEntity();
        ServerPlayer wearer = srcEntity instanceof ServerPlayer p ? p : null;
        if (wearer == null) {
            // 兜底：投射物（箭/法术弹）的拥有者是玩家也算击杀者
            Entity direct = source.getDirectEntity();
            if (direct instanceof net.minecraft.world.entity.projectile.Projectile proj
                    && proj.getOwner() instanceof ServerPlayer p) {
                wearer = p;
            }
        }
        LOLAccessories.LOGGER.info("[蜕生诊断] wearer={}", wearer != null ? wearer.getName().getString() : "null");
        if (wearer == null) {
            return;
        }
        boolean wearing = CuriosGearWear.isWearing(wearer, "cryptbloom");
        LOLAccessories.LOGGER.info("[蜕生诊断] isWearing(cryptbloom)={}, wearer==victim={}",
                wearing, wearer == victim);
        if (!wearing || wearer == victim) {
            return;
        }
        boolean enemy = isEnemyOf(wearer, victim);
        LOLAccessories.LOGGER.info("[蜕生诊断] isEnemyOf={}", enemy);
        if (!enemy) {
            return;
        }
        // 真正结算交给统一方法（与 handleAttackerSide 致死伤害兜底共用，冷却去重）
        triggerLifeFromDeath(wearer, victim);
    }

    /**
     * 蜕生·死中新生统一结算：冷却 / 配置 / 治疗 / 光球发送。
     * onLivingDeath 与 handleAttackerSide 的致死伤害兜底共用此方法，靠 CRYPTBLOOM_CD 去重，
     * 避免两条路径在同一击杀上重复触发。
     */
    private static void triggerLifeFromDeath(ServerPlayer wearer, LivingEntity victim) {
        UUID uuid = wearer.getUUID();
        long now = System.currentTimeMillis();
        Long cd = CRYPTBLOOM_CD.get(uuid);
        if (cd != null && now < cd) {
            return;
        }
        CRYPTBLOOM_CD.put(uuid, now + 60000L);
        GearConfig config = GearConfigManager.get("cryptbloom");
        if (config == null) {
            LOLAccessories.LOGGER.warn("[蜕生诊断] GearConfigManager.get(cryptbloom) 为 null（配置未加载）");
            return;
        }
        GearConfig.OnHitEffect effect = config.findEffect("life_from_death").orElse(null);
        if (effect == null) {
            LOLAccessories.LOGGER.warn("[蜕生诊断] life_from_death effect 为 null");
            return;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 4.0D;
        double base = effect.base_damage > 0 ? effect.base_damage : 100.0D;
        double heal = (base + spellPower(wearer)
                * (effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.20D))
                * (1.0D + healPowerBonus(wearer));
        if (wearer.level() instanceof ServerLevel level) {
            for (Player p : level.players()) {
                if (p == wearer) {
                    p.heal((float) heal);
                } else if (p.isAlliedTo(wearer) && p.isAlive()
                        && p.distanceTo(victim) <= radius + 1.0D) {
                    p.heal((float) heal);
                }
            }
            LOLAccessories.LOGGER.info("[蜕生] 死中新生触发：玩家 {} 击杀 {} @({},{},{}) 半径 {}",
                    wearer.getName().getString(), victim.getName().getString(),
                    (int) victim.getX(), (int) victim.getY(), (int) victim.getZ(), radius);
            LOLNetworking.sendSpotFx(level, FxKind.LIFE_FROM_DEATH,
                    victim.getX(), victim.getY() + 0.5D, victim.getZ(), 90, (float) radius);
        }
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

    /** 自然之力「坚韧」状态：受魔法伤害叠层，满层获得魔抗与移速，窗口过期自动清除。 */
    private static final class SteadfastState {
        private int stacks;
        private long expireMs;
    }

    /** 风暴狂涌「风暴掠袭」状态：按目标累计伤害，达标后延迟引爆。 */
    private static final class StormsurgeState {
        private final java.util.UUID wearer;
        private final LivingEntity target;
        private long windowUntilMs;
        private long explodeAtMs;
        private long cooldownUntilMs;
        private float damage;
        private boolean armed;
        private double baseDamage;
        private double apRatio;
        private String school;

        private StormsurgeState(java.util.UUID wearer, LivingEntity target, long windowUntilMs) {
            this.wearer = wearer;
            this.target = target;
            this.windowUntilMs = windowUntilMs;
        }
    }

    /** 裂隙制造者「虚空侵蚀」状态：层数、上次叠层与上次战斗（造成/承受伤害）毫秒。 */
    private static final class RiftState {
        private int stacks;
        private long lastStackMs;
        private long lastCombatMs;
    }

    private static final class FocusedWillState {
        private int stacks;
        private long lastDamageMs;
        private long lastStackMs;
        private long expireMs;
    }

    private static final class SkipperState {
        private int stacks;
        private long expireMs;
    }

    private static final class ShipwreckerState {
        private int shipwreckerStacks;
        private net.minecraft.world.phys.Vec3 lastPosition;
    }

    private static final class TerminusState {
        private int lightStacks;
        private int darkStacks;
        private boolean nextLight = true;
        private long expireMs;
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
