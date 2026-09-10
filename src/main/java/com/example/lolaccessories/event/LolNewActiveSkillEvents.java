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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
    /** 鼓舞（舒瑞娅的战歌）技能标识。 */
    public static final String SKILL_INSPIRING_SPEECH = "inspiring_speech";
    /** 鼓舞对应装备 gear_id。 */
    public static final String GEAR_SHURELYAS_BATTLESONG = "shurelyas_battlesong";
    /** 法力成真（实现器）技能标识。 */
    public static final String SKILL_REALIZE = "realize";
    /** 法力成真对应装备 gear_id。 */
    public static final String GEAR_ACTUALIZER = "actualizer";

    /** 冷却缩减对主动技冷却的减免上限（与时间停止一致，保守取 40%）。 */
    private static final double MAX_COOLDOWN_REDUCTION = 0.4D;

    /** 每个技能（skillId → 玩家 UUID → 上次成功触发的系统毫秒）。 */
    private static final Map<String, Map<UUID, Long>> LAST_USE_MS = new HashMap<>();

    private LolNewActiveSkillEvents() {
    }

    /** 由 {@code ActiveSkillTriggerPacket} 在服务端主线程调用：按 skillId 分发触发。 */
    public static void tryTrigger(ServerPlayer player, String skillId) {
        switch (skillId) {
            case SKILL_QUICKSILVER -> trigger(player, skillId, GEAR_QUICKSILVER,
                    ModItems.QUICKSILVER_SASH.get().getDefaultInstance().getHoverName(),
                    () -> cleanse(player));
            case SKILL_CRESCENT -> trigger(player, skillId, GEAR_TIAMAT,
                    ModItems.TIAMAT.get().getDefaultInstance().getHoverName(),
                    () -> crescent(player));
            case SKILL_INSPIRING_SPEECH -> trigger(player, skillId, GEAR_SHURELYAS_BATTLESONG,
                    ModItems.SHURELYAS_BATTLESONG.get().getDefaultInstance().getHoverName(),
                    () -> inspiringSpeech(player));
            // 法力成真（实现器）：冷却固定为配置值，不受任何冷却缩减影响（自身就是“令冷却近似归零”的来源）
            case SKILL_REALIZE -> trigger(player, skillId, GEAR_ACTUALIZER,
                    ModItems.ACTUALIZER.get().getDefaultInstance().getHoverName(),
                    () -> realize(player), false);
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
        long cooldownMs = Math.max(1000L, Math.round(baseCooldownSec * 1000.0D * (1.0D - reduction)));

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

    /** 新月：对周围敌对生物造成一次物理伤害（参数来自 tiamat.json 的 crescent）。返回命中数。 */
    private static int crescent(ServerPlayer player) {
        GearConfig config = GearConfigManager.get(GEAR_TIAMAT);
        GearConfig.OnHitEffect effect = config.findEffect(SKILL_CRESCENT).orElse(null);
        if (effect == null) {
            return 0;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D;
        float damage = (float) (effect.base_damage > 0 ? effect.base_damage : 14.0D);
        int hits = 0;
        for (LivingEntity enemy : LolNewEpicPassiveEvents.enemiesAround(player, player, radius)) {
            enemy.hurt(player.level().damageSources().playerAttack(player), damage);
            hits++;
        }
        return hits;
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
}
