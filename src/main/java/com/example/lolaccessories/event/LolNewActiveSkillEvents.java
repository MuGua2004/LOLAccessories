package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.init.ModMobEffects;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.GearFxBroadcast;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.entity.LightningOrbEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * 34 件新 2 级（史诗）装备中的主动技能（服务端结算）。
 *
 * <p>触发方式与「时间停止」同链路：每个主动技能对应一个按键（默认不绑定，玩家在按键设置
 * 「LOL 饰品」分类自行分配），客户端按键只发送 {@code ActiveSkillTriggerPacket}，这里按
 * {@code skillId} 统一做穿戴校验、冷却判定并结算。当前支持：</p>
 *
 * <ul>
 *   <li>{@link #SKILL_QUICKSILVER}（水银饰带·净化）：解除自身全部有害状态（控制与减益）
 *       并扑灭火焰，返回解除数量；</li>
 *   <li>{@link #SKILL_CRESCENT}（提亚马特·新月）：对周围敌对生物造成一次物理伤害，
 *       返回命中数量。</li>
 * </ul>
 *
 * <p>冷却结算使用单调系统毫秒（跨维度/换存档不卡冷却），基础冷却来自对应 gear 配置的
 * {@code cooldown_seconds}，并享受铁魔法「冷却缩减」属性减免（上限 {@link #MAX_COOLDOWN_REDUCTION}，
 * 与时间停止一致）；就绪时通过 {@link LOLNetworking#sendSkillCooldown} 同步给客户端 HUD。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolNewActiveSkillEvents {

    /** 净化（水银饰带）技能标识。 */
    public static final String SKILL_QUICKSILVER = "quicksilver";
    /** 净化对应装备 gear_id。 */
    public static final String GEAR_QUICKSILVER = "quicksilver_sash";
    /** 新月（提亚马特）技能标识。 */
    public static final String SKILL_CRESCENT = "crescent";
    /** 新月对应装备 gear_id。 */
    public static final String GEAR_TIAMAT = "tiamat";
    /** 新月对应装备 gear_id（贪欲九头蛇，与提亚马特共用新月按键）。 */
    public static final String GEAR_RAVENOUS_HYDRA = "ravenous_hydra";
    /** 新月对应装备 gear_id（巨型九头蛇，与提亚马特共用新月按键）。 */
    public static final String GEAR_TITANIC_HYDRA = "titanic_hydra";
    /** 亵渎九头蛇（与九头蛇系列共用新月按键）。 */
    public static final String GEAR_PROFANE_HYDRA = "profane_hydra";
    /** 鼓舞（舒瑞娅的战歌）技能标识。 */
    public static final String SKILL_INSPIRING_SPEECH = "inspiring_speech";
    /** 鼓舞对应装备 gear_id。 */
    public static final String GEAR_SHURELYAS_BATTLESONG = "shurelyas_battlesong";
    /** 法力成真（实现器）技能标识。 */
    public static final String SKILL_REALIZE = "realize";
    /** 法力成真对应装备 gear_id。 */
    public static final String GEAR_ACTUALIZER = "actualizer";
    /** 嘲弄命运（命运十面骰）技能标识。 */
    public static final String SKILL_MOCK_FATE = "mock_fate";
    /** 嘲弄命运对应装备 gear_id。 */
    public static final String GEAR_FATE_DIE = "fate_die";
    /** 誓约（骑士之誓）技能标识。 */
    public static final String SKILL_PLEDGE = "pledge";
    /** 誓约对应装备 gear_id。 */
    public static final String GEAR_KNIGHTS_VOW = "knights_vow";
    /** 降临（救赎）技能标识。 */
    public static final String SKILL_INTERVENTION = "intervention";
    /** 降临对应装备 gear_id。 */
    public static final String GEAR_REDEMPTION = "redemption";
    /** 水银（水银弯刀）技能标识。 */
    public static final String SKILL_MERCURIAL = "mercurial";
    /** 水银对应装备 gear_id。 */
    public static final String GEAR_MERCURIAL = "mercurial_scimitar";
    /** 鬼步（幽梦之灵）技能标识。 */
    public static final String SKILL_WRAITH_STEP = "wraith_step";
    /** 鬼步对应装备 gear_id。 */
    public static final String GEAR_YOUMUUS = "youmuus_ghostblade";
    /** 海克斯科技枪刃（Hextech Gunblade）主动技能标识。 */
    public static final String SKILL_GUNBLADE = "gunblade";
    /** 海克斯科技枪刃对应装备 gear_id。 */
    public static final String GEAR_GUNBLADE = "hextech_gunblade";
    /** 海克斯科技火箭腰带（Hextech Rocketbelt）主动技能标识。 */
    public static final String SKILL_ROCKETBELT = "rocketbelt";
    /** 海克斯科技火箭腰带对应装备 gear_id。 */
    public static final String GEAR_ROCKETBELT = "hextech_rocketbelt";
    /** 兰顿之兆（Randuin's Omen）主动技能标识。 */
    public static final String SKILL_RANDUINS = "randuins_active";
    /** 兰顿之兆对应装备 gear_id。 */
    public static final String GEAR_RANDUINS = "randuins_omen";
    /** 再见桃花源（翡翠城）主动技能标识。 */
    public static final String SKILL_FAREWELL_PARADISE = "farewell_paradise";
    /** 再见桃花源对应装备 gear_id。 */
    public static final String GEAR_EMERALD_CITY = "emerald_city";
    /** 挺进破坏者（Stridebreaker）主动技能标识。 */
    public static final String SKILL_STRIDEBREAKER = "shockwave";
    /** 挺进破坏者对应装备 gear_id。 */
    public static final String GEAR_STRIDEBREAKER = "stridebreaker";
    /** 此恨无绝（灵恸）主动技能标识。 */
    public static final String SKILL_ENDLESS_GRIEF = "endless_grief";
    /** 此恨无绝对应装备 gear_id。 */
    public static final String GEAR_SOULS_LAMENT = "souls_lament";

    /** 冷却缩减对主动技冷却的减免上限（与时间停止一致，保守取 40%）。 */
    private static final double MAX_COOLDOWN_REDUCTION = 0.4D;

    /** 每个技能（skillId → 玩家 UUID → 上次成功触发的系统毫秒）。 */
    private static final Map<String, Map<UUID, Long>> LAST_USE_MS = new HashMap<>();
    /** 巨型九头蛇刚斩：玩家 UUID → 下一次普攻强化的到期时间。 */
    private static final Map<UUID, Long> TITANIC_CRESCENT_READY = new HashMap<>();

    private LolNewActiveSkillEvents() {
    }

    /** 由 {@code ActiveSkillTriggerPacket} 在服务端主线程调用：按 skillId 分发触发。 */
    public static void tryTrigger(ServerPlayer player, String skillId) {
        switch (skillId) {
            case SKILL_QUICKSILVER -> trigger(player, skillId, GEAR_QUICKSILVER,
                    ModItems.QUICKSILVER_SASH.get().getDefaultInstance().getHoverName(),
                    () -> cleanse(player));
            // 新月：提亚马特、贪欲九头蛇与巨型九头蛇共用同一按键与技能 id，按当前佩戴的最高级装备取配置。
            case SKILL_CRESCENT -> {
                String gear = pickWorn(player, GEAR_TIAMAT, GEAR_RAVENOUS_HYDRA, GEAR_TITANIC_HYDRA, GEAR_PROFANE_HYDRA);
                Component name = switch (gear) {
                    case GEAR_RAVENOUS_HYDRA -> ModItems.RAVENOUS_HYDRA.get().getDefaultInstance().getHoverName();
                    case GEAR_TITANIC_HYDRA -> ModItems.TITANIC_HYDRA.get().getDefaultInstance().getHoverName();
                    case GEAR_PROFANE_HYDRA -> ModItems.PROFANE_HYDRA.get().getDefaultInstance().getHoverName();
                    default -> ModItems.TIAMAT.get().getDefaultInstance().getHoverName();
                };
                trigger(player, skillId, gear, name, () -> crescent(player, gear));
            }
            case SKILL_INSPIRING_SPEECH -> trigger(player, skillId, GEAR_SHURELYAS_BATTLESONG,
                    ModItems.SHURELYAS_BATTLESONG.get().getDefaultInstance().getHoverName(),
                    () -> inspiringSpeech(player));
            // 法力成真（实现器）：冷却固定为配置值，不受任何冷却缩减影响（自身就是“令冷却近似归零”的来源）
            case SKILL_REALIZE -> trigger(player, skillId, GEAR_ACTUALIZER,
                    ModItems.ACTUALIZER.get().getDefaultInstance().getHoverName(),
                    () -> realize(player), false);
            // 嘲弄命运：掷骰本体在 LolFateDiceEvents（持续 30 秒的状态管理与结算也在那边）
            case SKILL_MOCK_FATE -> trigger(player, skillId, GEAR_FATE_DIE,
                    ModItems.FATE_DIE.get().getDefaultInstance().getHoverName(),
                    () -> com.example.lolaccessories.event.LolFateDiceEvents.rollFateChecked(player));
            // 誓约：与准星方向的友方生物（含玩家）缔结系链（本体逻辑在 LolKnightVowEvents）
            case SKILL_PLEDGE -> trigger(player, skillId, GEAR_KNIGHTS_VOW,
                    ModItems.KNIGHTS_VOW.get().getDefaultInstance().getHoverName(),
                    () -> com.example.lolaccessories.event.LolKnightVowEvents.pledge(player));
            // 降临（救赎）：以施法时的玩家坐标为中心，2.5 秒后召唤圣光（敌伤友疗）
            case SKILL_INTERVENTION -> trigger(player, skillId, GEAR_REDEMPTION,
                    ModItems.REDEMPTION.get().getDefaultInstance().getHoverName(),
                    () -> intervention(player));
            // 水银（水银弯刀）：复用饰带净化并获短时移速
            case SKILL_MERCURIAL -> trigger(player, skillId, GEAR_MERCURIAL,
                    ModItems.MERCURIAL_SCIMITAR.get().getDefaultInstance().getHoverName(),
                    () -> mercurial(player));
            // 鬼步（幽梦之灵）：施加 6 秒 +20% 移速的鬼步窗口（同时无视单位碰撞）
            case SKILL_WRAITH_STEP -> trigger(player, skillId, GEAR_YOUMUUS,
                    ModItems.YOUMUUS_GHOSTBLADE.get().getDefaultInstance().getHoverName(),
                    () -> wraithStep(player));
            case SKILL_GUNBLADE -> trigger(player, skillId, GEAR_GUNBLADE,
                    ModItems.HEXTECH_GUNBLADE.get().getDefaultInstance().getHoverName(),
                    () -> gunblade(player));
            case SKILL_ROCKETBELT -> trigger(player, skillId, GEAR_ROCKETBELT,
                    ModItems.HEXTECH_ROCKETBELT.get().getDefaultInstance().getHoverName(),
                    () -> rocketbelt(player));
            case SKILL_RANDUINS -> trigger(player, skillId, GEAR_RANDUINS,
                    ModItems.RANDUINS_OMEN.get().getDefaultInstance().getHoverName(),
                    () -> randuinsActive(player));
            // 再见桃花源（翡翠城）：抹杀叠满琼华的生物并使其战利品翻倍（本体逻辑在 LolEmeraldCityEvents）
            case SKILL_FAREWELL_PARADISE -> trigger(player, skillId, GEAR_EMERALD_CITY,
                    ModItems.EMERALD_CITY.get().getDefaultInstance().getHoverName(),
                    () -> com.example.lolaccessories.event.LolEmeraldCityEvents.farewellParadise(player));
            // 挺进破坏者：破阵冲击波（80%AD 物理伤害 + 减速 + 衰减移速，本体在 LolFifthBatchPassiveEvents）
            case SKILL_STRIDEBREAKER -> trigger(player, skillId, GEAR_STRIDEBREAKER,
                    ModItems.STRIDEBREAKER.get().getDefaultInstance().getHoverName(),
                    () -> com.example.lolaccessories.event.LolFifthBatchPassiveEvents.shockwave(player));
            // 此恨无绝（灵恸）：30 秒近战虚空化窗口（本体在 LolSorrowPoemEvents）
            case SKILL_ENDLESS_GRIEF -> trigger(player, skillId, GEAR_SOULS_LAMENT,
                    ModItems.SOULS_LAMENT.get().getDefaultInstance().getHoverName(),
                    () -> com.example.lolaccessories.event.LolSorrowPoemEvents.endlessGrief(player));
            default -> LOLAccessories.LOGGER.warn("[主动技能] 收到未知技能 id：{}", skillId);
        }
    }

    /**
     * 通用主动技触发链（享受铁魔法冷却缩减减免）。
     *
     * @param action 效果结算，返回给玩家展示的数量（净化数 / 新月命中数）。
     */
    private static void trigger(ServerPlayer player, String skillId, String gearId,
                                Component itemName, IntSupplier action) {
        trigger(player, skillId, gearId, itemName, action, true);
    }

    /**
     * 减少进行中的主动技冷却（灵恸·此恨无绝：每击杀一个敌对生物 -5 秒）。
     * 只在冷却确实进行中时生效；减少后刷新客户端 HUD 进度条。
     */
    public static void reduceRemainingCooldown(ServerPlayer player, String skillId,
                                               String gearId, long reduceMs) {
        Map<UUID, Long> cds = LAST_USE_MS.get(skillId);
        if (cds == null) {
            return;
        }
        Long lastMs = cds.get(player.getUUID());
        if (lastMs == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long cooldownMs = 300_000L;
        GearConfig.OnHitEffect skill = GearConfigManager.get(gearId).findEffect(skillId).orElse(null);
        if (skill != null && skill.cooldown_seconds > 0) {
            double reduction = Math.min(MAX_COOLDOWN_REDUCTION,
                    Math.max(0.0D, readCooldownReduction(player)));
            cooldownMs = Math.max(1000L, Math.round(skill.cooldown_seconds * 1000.0D
                    * (1.0D - reduction)
                    * com.example.lolaccessories.util.HasteMath.gearHasteFactor(player)));
        }
        long elapsed = nowMs - lastMs;
        if (elapsed >= cooldownMs) {
            return; // 不在冷却中，无从减免
        }
        long applied = Math.min(reduceMs, cooldownMs - elapsed);
        cds.put(player.getUUID(), lastMs - applied);
        long remainingTicks = Math.max(1L, (cooldownMs - (nowMs - (lastMs - applied))) / 50L);
        LOLNetworking.sendSkillCooldown(player, skillId, (int) remainingTicks);
    }

    /**
     * 通用主动技触发链：穿戴校验 → 冷却判定 → 结算效果 → 同步冷却给 HUD 并回执。
     *
     * @param scaleByCooldownReduction 冷却是否受铁魔法「冷却缩减」属性减免
     *                                 （法力成真固定不受减免）。
     * @param action                   效果结算，返回给玩家展示的数量。
     */
    private static void trigger(ServerPlayer player, String skillId, String gearId,
                                Component itemName, IntSupplier action,
                                boolean scaleByCooldownReduction) {
        long nowMs = System.currentTimeMillis();
        if (!player.isAlive()) {
            return;
        }
        GearConfig config = GearConfigManager.get(gearId);
        if (config == null) {
            return;
        }
        GearConfig.OnHitEffect skill = config.findEffect(skillId).orElse(null);
        if (skill == null || !skill.enabled) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, gearId)) {
            actionBar(player, Component.translatable(
                    "skill.lolaccessories." + skillId + ".need_item", itemName));
            return;
        }

        double baseCooldownSec = Math.max(1.0D, skill.cooldown_seconds);
        double reduction = scaleByCooldownReduction
                ? Math.max(0.0D, Math.min(MAX_COOLDOWN_REDUCTION,
                readCooldownReduction(player)))
                : 0.0D;
        // 装备技能急速独立乘区（LoL 公式：CDR = 急速/(100+急速)）
        long cooldownMs = Math.max(1000L, Math.round(baseCooldownSec * 1000.0D
                * (1.0D - reduction)
                * com.example.lolaccessories.util.HasteMath.gearHasteFactor(player)));

        UUID uuid = player.getUUID();
        Map<UUID, Long> cds = LAST_USE_MS.computeIfAbsent(skillId, k -> new HashMap<>());
        Long lastMs = cds.get(uuid);
        if (lastMs != null && nowMs - lastMs < cooldownMs) {
            long remainingSec = (long) Math.ceil((cooldownMs - (nowMs - lastMs)) / 1000.0D);
            actionBar(player, Component.translatable(
                    "skill.lolaccessories." + skillId + ".cooldown", remainingSec));
            return;
        }
        cds.put(uuid, nowMs);
        if (cds.size() > 128) {
            cds.entrySet().removeIf(entry -> nowMs - entry.getValue() > 30L * 60L * 1000L);
        }

        int result = action.getAsInt();
        // 成就：第一次成功释放主动技能
        LolAdvancementService.onActiveSkillCast(player);

        // 同步冷却给客户端做 HUD 进度条（只在确认触发后发送；HUD 用游戏刻换算）
        long cooldownTicks = Math.max(1L, cooldownMs / 50L);
        LOLNetworking.sendSkillCooldown(player, skillId, (int) cooldownTicks);

        LOLAccessories.LOGGER.info("[主动技能] {} 触发 {}：结算 {}，基础冷却 {}s（冷却缩减 {}% → {}ms）",
                player.getName().getString(), skillId, result,
                fmt(baseCooldownSec), Math.round(reduction * 100.0D), cooldownMs);
    }

    /** 净化：解除自身全部有害状态（各类控制与减益）并扑灭火焰。返回解除的数量。 */
    private static int cleanse(ServerPlayer player) {
        List<MobEffectInstance> harmful = player.getActiveEffects().stream()
                .filter(instance -> instance.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        int cleared = 0;
        for (MobEffectInstance instance : harmful) {
            player.removeEffect(instance.getEffect());
            cleared++;
        }
        if (player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(0);
        }
        return cleared;
    }

    /** 新月：提亚马特/贪欲立即横扫；巨型九头蛇的刚斩则强化十秒内的下一次普攻。 */
    private static int crescent(ServerPlayer player, String gearId) {
        GearConfig config = GearConfigManager.get(gearId);
        GearConfig.OnHitEffect effect = config.findEffect(SKILL_CRESCENT).orElse(null);
        if (effect == null) {
            return 0;
        }
        if (GEAR_TITANIC_HYDRA.equals(gearId)) {
            TITANIC_CRESCENT_READY.put(player.getUUID(), System.currentTimeMillis()
                    + Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D) * 1000.0D));
            return 1;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D;
        double damageValue = effect.base_damage
                + (effect.power_ratio > 0 ? effect.power_ratio
                        * player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 0.0D)
                + (effect.max_health_pct > 0 ? effect.max_health_pct * player.getMaxHealth() : 0.0D);
        float damage = (float) Math.max(1.0D, damageValue);
        int hits = 0;
        for (LivingEntity enemy : LolNewEpicPassiveEvents.enemiesAround(player, player, radius)) {
            enemy.hurt(player.level().damageSources().playerAttack(player), damage);
            LolNewEpicPassiveEvents.slashFx(player, enemy);
            hits++;
        }
        return hits;
    }

    /** 消费刚斩预备状态；仅在其十秒窗口内的下一次普攻调用时返回 true。 */
    public static boolean consumeTitanicCrescent(ServerPlayer player) {
        Long expiresAt = TITANIC_CRESCENT_READY.remove(player.getUUID());
        return expiresAt != null && System.currentTimeMillis() <= expiresAt;
    }

    // ------------------------------------------------------------------ //
    // 降临（救赎）：以施法时的玩家坐标为中心，延迟后圣光落下（敌人受伤 / 友方治疗）
    // ------------------------------------------------------------------ //

    /** 一条待落地的降临（施法坐标固定，不跟随玩家）。 */
    private record PendingDescent(ServerPlayer owner, double x, double y, double z,
                                  float damagePct, float healPct, double radius, long landAtMs) {
    }

    private static final Map<UUID, PendingDescent> PENDING_DESCENTS = new HashMap<>();

    /** 降临施法：登记落地队列 + 播放施法预告法阵（锚定施法点坐标）。 */
    private static int intervention(ServerPlayer player) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_REDEMPTION)
                .findEffect(SKILL_INTERVENTION).orElse(null);
        if (effect == null || !effect.enabled || !(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.5D;
        long delayMs = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 2.5D) * 1000.0D);
        PENDING_DESCENTS.put(player.getUUID(), new PendingDescent(player,
                player.getX(), player.getY(), player.getZ(),
                (float) (effect.base_damage > 0 ? effect.base_damage : 0.10D),
                (float) (effect.amount > 0 ? effect.amount : 0.25D),
                radius, System.currentTimeMillis() + delayMs));
        // 施法预告法阵：预告期 + 24 tick 落下期（地点特效，锚定施法点）
        LOLNetworking.sendSpotFx(level, FxKind.REDEMPTION_CAST,
                player.getX(), player.getY(), player.getZ(), (int) (delayMs / 50L) + 24, (float) radius);
        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return 1;
    }

    /** 圣光落地：范围内敌人受「最大生命 × 比例」的神圣魔法伤害，友方（含自己）按同比例回复。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clearExpiredMercurialMs();
        if (PENDING_DESCENTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        PENDING_DESCENTS.entrySet().removeIf(entry -> {
            PendingDescent d = entry.getValue();
            if (now < d.landAtMs()) {
                return false;
            }
            if (!d.owner().isAlive()
                    || d.owner().getServer() == null
                    || d.owner().getServer().getPlayerList().getPlayer(d.owner().getUUID()) == null
                    || !(d.owner().level() instanceof ServerLevel level)) {
                return true;
            }
            landDescent(level, d);
            return true;
        });
    }

    private static void landDescent(ServerLevel level, PendingDescent d) {
        ServerPlayer owner = d.owner();
        // 圣光落下特效 + 落点音效
        LOLNetworking.sendSpotFx(level, FxKind.REDEMPTION_DESCENT, d.x(), d.y(), d.z(), 24, (float) d.radius());
        level.playSound(null, net.minecraft.core.BlockPos.containing(d.x(), d.y(), d.z()),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        // 服务端粒子兜底反馈（不依赖自绘渲染链路，特效关闭时也能看到）
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                d.x(), d.y() + 1.0D, d.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                d.x(), d.y() + 0.6D, d.z(), 40, 2.2D, 0.5D, 2.2D, 0.06D);
        var school = com.example.lolaccessories.compat.IronsSpellDamage.resolve("radiant");
        double healPower = 1.0D + Math.max(0.0D,
                owner.getAttributeValue(com.example.lolaccessories.init.ModAttributes.LOL_HEAL_POWER.get()));
        int healed = 0;
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(d.x(), d.y(), d.z(), d.x(), d.y(), d.z())
                        .inflate(d.radius(), 2.0D, d.radius()),
                e -> e.isAlive() && e.distanceToSqr(d.x(), d.y(), d.z()) <= d.radius() * d.radius())) {
            if (e == owner || !LolNewEpicPassiveEvents.isEnemyOf(owner, e)) {
                // 友方（含施法者本人）：回复最大生命 × 比例（受治疗与护盾强度加成）
                e.heal((float) (e.getMaxHealth() * d.healPct() * healPower));
                healed++;
            } else {
                // 敌人：最大生命 × 比例的神圣魔法伤害
                com.example.lolaccessories.compat.IronsSpellDamage.apply(
                        owner, e, (float) (e.getMaxHealth() * d.damagePct()), school);
                hit++;
            }
        }
        actionBar(owner, Component.translatable("skill.lolaccessories.intervention.result", healed, hit));
    }

    /**
     * 水银（水银弯刀）：解除自身全部有害状态（复用饰带净化逻辑），并获得短时移动速度加成。
     * 返回解除的有害状态数量（供 HUD 回执）。
     */
    private static int mercurial(ServerPlayer player) {
        int cleared = cleanse(player);
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_MERCURIAL)
                .findEffect(SKILL_MERCURIAL).orElse(null);
        double ratio = effect != null && effect.amount > 0 ? effect.amount : 0.50D;
        int durationTicks = (int) Math.max(20L, Math.round(
                (effect != null && effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 20.0F));
        applyTimedSpeed(player, ratio, durationTicks);
        return cleared;
    }

    /**
     * 鬼步（幽梦之灵）：施加 6 秒的「鬼步」增益效果——由 {@code WraithStepEffect} 提供 +20% 移动速度，
     * 并经 {@code PhantomDancerCollisionMixin} 视为「无视单位碰撞」窗口（复用幻影之舞的穿人机制）。
     */
    private static int wraithStep(ServerPlayer player) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_YOUMUUS)
                .findEffect(SKILL_WRAITH_STEP).orElse(null);
        int durationTicks = (int) Math.max(20L, Math.round(
                (effect != null && effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D) * 20.0F));
        player.addEffect(new MobEffectInstance(ModMobEffects.WRAITH_STEP.get(),
                durationTicks, 0, false, true));
        return 1;
    }

    /** 水银弯刀的短时移速加成（transient 修饰符，到期由 {@link #clearExpiredMercurialMs} 移除）。 */
    private static final UUID MERCURIAL_MS_UUID =
            UUID.fromString("c4f1a2b3-9d8e-4c7f-8a6b-5d4e3f2a1b0c");
    private static final Map<UUID, Long> MERCURIAL_MS_EXPIRE = new HashMap<>();

    private static void applyTimedSpeed(ServerPlayer player, double ratio, int durationTicks) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        if (attr.getModifier(MERCURIAL_MS_UUID) != null) {
            attr.removeModifier(MERCURIAL_MS_UUID);
        }
        attr.addTransientModifier(new AttributeModifier(MERCURIAL_MS_UUID, "mercurial_ms", ratio,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        MERCURIAL_MS_EXPIRE.put(player.getUUID(), System.currentTimeMillis() + durationTicks * 50L);
    }

    /** 到期后移除水银弯刀的短时移速修饰符。 */
    private static void clearExpiredMercurialMs() {
        if (MERCURIAL_MS_EXPIRE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        MERCURIAL_MS_EXPIRE.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    var attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (attr != null && attr.getModifier(MERCURIAL_MS_UUID) != null) {
                        attr.removeModifier(MERCURIAL_MS_UUID);
                    }
                }
            }
            return true;
        });
    }

    /** 返回 player 实际佩戴的第一个指定 gearId；都没戴返回 null。 */
    private static String pickWorn(ServerPlayer player, String... gearIds) {
        java.util.Set<String> wanted = new java.util.HashSet<>(java.util.Arrays.asList(gearIds));
        String[] found = {null};
        com.example.lolaccessories.compat.CuriosGearWear.forEachEquippedGear(player, gear -> {
            if (found[0] == null && wanted.contains(gear.getGearId())) {
                found[0] = gear.getGearId();
            }
        });
        return found[0];
    }

    /**
     * 鼓舞（舒瑞娅的战歌）：对佩戴者与附近友方玩家（只作用于玩家，不对任何生物生效）施加
     * 短时移速加成（参数来自 shurelyas_battlesong.json 的 inspiring_speech）。返回受影响人数。
     */
    private static int inspiringSpeech(ServerPlayer player) {
        GearConfig config = GearConfigManager.get(GEAR_SHURELYAS_BATTLESONG);
        GearConfig.OnHitEffect effect = config == null ? null
                : config.findEffect(SKILL_INSPIRING_SPEECH).orElse(null);
        if (effect == null) {
            return 0;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 100.0D;
        int durationTicks = (int) Math.max(20L, Math.round(
                (effect.duration_seconds > 0 ? effect.duration_seconds : 4.0D) * 20.0F));
        int count = 0;
        for (ServerPlayer ally : LolLegendPassiveEvents.allyPlayersAround(player, radius)) {
            if (!ally.isAlive() || ally.isSpectator()) {
                continue;
            }
            ally.addEffect(new MobEffectInstance(
                    ModMobEffects.INSPIRING_SPEECH.get(), durationTicks, 0, false, true));
            GearFxBroadcast.window(ally, FxKind.INSPIRE, durationTicks);
            count++;
        }
        // 施放音效：磬鸣般的“战歌启动”声（原版音效近似，待专属音频到位后可替换）
        if (count > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
        }
        return count;
    }

    /**
     * 法力成真（实现器）：开启 5 秒（配置）窗口——期间铁魔法施法法力消耗翻倍、冷却几乎立即可用。
     * 冷却固定 180 秒且不受冷却缩减影响（走 {@link #trigger} 的 fixed 分支）。
     */
    private static int realize(ServerPlayer player) {
        Lol2026LegendPassiveEvents.activateRealize(player);
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        LAST_USE_MS.values().forEach(map -> map.remove(uuid));
        MERCURIAL_MS_EXPIRE.remove(uuid);
    }

    private static void actionBar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * 读取铁魔法冷却缩减属性的净加成（值 1.1 → +0.1 = 10%）；未装铁魔法时返回 0。
     */
    private static double readCooldownReduction(LivingEntity entity) {
        ResourceLocation id = ResourceLocation.tryParse(IronsCompat.COOLDOWN_REDUCTION);
        if (id == null) {
            return 0.0D;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute) - attribute.getDefaultValue();
    }

    private static String fmt(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /**
     * 海克斯科技枪刃（Hextech Gunblade）主动：朝准星方向发射一颗闪电弹球，命中目标时
     * 造成「当前生命 + 最大生命各 6%」的魔法伤害，并治疗施法者该伤害的 50%；
     * 无目标则向前电光突进（命中阶段不结算，仅作视觉）。
     */
    private static int gunblade(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_GUNBLADE)
                .findEffect(SKILL_GUNBLADE).orElse(null);
        String school = effect != null && !effect.school.isEmpty() ? effect.school : "lightning";
        double baseDamage = effect != null && effect.base_damage > 0 ? effect.base_damage : 150.0D;
        double apRatio = effect != null && effect.power_ratio > 0 ? effect.power_ratio : 0.40D;
        // 本模组法术强度为倍率属性（默认 1.0，+80% → 1.8）；按“每 1% = 1 AP”折算，
        // 40% 法术强度 = (spell_power − 1.0) × 100 × 0.40。
        double apAmount = Math.max(0.0D,
                readAttribute(player, "irons_spellbooks:spell_power") - 1.0D) * 100.0D * apRatio;
        LivingEntity target = pickCrosshairTarget(player, 32.0D);
        float damage = (float) (baseDamage + apAmount);
        float heal = target != null ? damage * 0.5F : 0.0F;
        Vec3 start = player.getEyePosition();
        Vec3 outward = player.getLookAngle();
        LightningOrbEntity.launch(level, player, target, damage, heal, school, start, outward);
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
        }
        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        LOLAccessories.LOGGER.info("[枪刃] {} 发射闪电弹球：目标 {}，伤害 {}",
                player.getName().getString(),
                target != null ? target.getName().getString() : "无", fmt(damage));
        return target != null ? 1 : 0;
    }

    /**
     * 兰顿之兆（Randuin's Omen）主动：减速光环——减速附近敌方单位移动速度（配置 ratio，默认 35%）持续 2 秒。
     */
    private static int randuinsActive(ServerPlayer player) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_RANDUINS)
                .findEffect(SKILL_RANDUINS).orElse(null);
        double ratio = effect != null && effect.amount > 0 ? effect.amount : 0.35D;
        double radius = effect != null && effect.radius_blocks > 0 ? effect.radius_blocks : 5.0D;
        long durationTicks = Math.round(
                (effect != null && effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 20.0D);
        int slowLevel = Math.max(0, (int) Math.round(ratio / 0.15D) - 1);
        int hits = 0;
        for (LivingEntity enemy : LolNewEpicPassiveEvents.enemiesAround(player, player, radius)) {
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) durationTicks, slowLevel, false, true));
            hits++;
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 0.9F);
        LOLAccessories.LOGGER.info("[兰顿] {} 减速光环：{} 名敌人被减速", player.getName().getString(), hits);
        return hits;
    }

    /**
     * 海克斯科技火箭腰带（Hextech Rocketbelt）主动：向准星方向冲刺，并以 60° 扇形发射 7 枚法球。
     */
    private static int rocketbelt(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_ROCKETBELT)
                .findEffect(SKILL_ROCKETBELT).orElse(null);
        float baseDamage = effect != null && effect.base_damage > 0 ? (float) effect.base_damage : 100.0F;
        String school = effect != null && !effect.school.isEmpty() ? effect.school : "fire";
        double apRatio = effect != null && effect.power_ratio > 0 ? effect.power_ratio : 0.10D;
        double apAmount = Math.max(0.0D,
                readAttribute(player, "irons_spellbooks:spell_power") - 1.0D) * 100.0D * apRatio;
        float damage = (float) (baseDamage + apAmount);
        Vec3 start = player.getEyePosition();
        Vec3 outward = player.getLookAngle().normalize();
        int launched = 0;
        for (int i = -3; i <= 3; i++) {
            Vec3 direction = rotateHorizontal(outward, i * 10.0D);
            EchoOrbEntity.launchStraight(level, player, damage, school, start, direction);
            launched++;
        }
        // 火箭腰带必须提供一次向准星方向的短距离冲刺；显式同步最终速度，避免客户端预测覆盖位移。
        player.push(outward.x * 1.65D, 0.28D, outward.z * 1.65D);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.1F);
        LOLAccessories.LOGGER.info("[火箭腰带] {} 冲刺并发射 {} 枚扇形法球，每枚伤害 {}",
                player.getName().getString(), launched, fmt(damage));
        return launched;
    }

    /** 将水平朝向绕 Y 轴旋转，用于火箭腰带的扇形弹幕。 */
    private static Vec3 rotateHorizontal(Vec3 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(direction.x * cos - direction.z * sin, direction.y,
                direction.x * sin + direction.z * cos).normalize();
    }

    /** 读取某属性相对默认值的净加成（属性不存在返回 0）。 */
    private static double readAttribute(LivingEntity entity, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return 0.0D;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(rl);
        if (attribute == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute) - attribute.getDefaultValue();
    }

    /** 选取玩家准星方向（前方约 72° 锥内、射程内、且最对准的敌对生物）；无则返回 null。 */
    private static LivingEntity pickAimedTarget(ServerPlayer player, double reach) {
        return pickAimedTarget(player, reach, player.getLookAngle());
    }

    /** 选取与准星夹角最小的敌对目标；夹角相同时优先更近者。 */
    private static LivingEntity pickCrosshairTarget(ServerPlayer player, double reach) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 eye = player.getEyePosition();
        LivingEntity best = null;
        double bestDot = 0.3D;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach),
                entity -> entity.isAlive() && LolNewEpicPassiveEvents.isEnemyOf(player, entity))) {
            Vec3 to = entity.getEyePosition().subtract(eye);
            double distance = to.length();
            if (distance > reach || distance < 1.0E-3D) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot > bestDot || (Math.abs(dot - bestDot) < 1.0E-6D && distance < bestDistance)) {
                bestDot = dot;
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** 按指定方向选取最对准的敌对生物，供其他主动技能复用。 */
    private static LivingEntity pickAimedTarget(ServerPlayer player, double reach, Vec3 direction) {
        Vec3 look = direction.normalize();
        Vec3 eye = player.getEyePosition();
        LivingEntity best = null;
        double bestScore = -1.0D;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach),
                e -> e.isAlive() && LolNewEpicPassiveEvents.isEnemyOf(player, e))) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 1.0E-3D) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot < 0.3D) {
                continue;
            }
            double score = dot / dist;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }
}
