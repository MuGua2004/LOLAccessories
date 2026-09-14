package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 主动技能「时间停止」：探索者的护臂（Seeker's Armguard）在 Curios 手镯栏佩戴时可用。
 *
 * <p>触发方式：客户端按键（按键默认不绑定，玩家在按键设置「LOL 饰品」分类自行分配）
 * 发送 {@code TimeStopTriggerPacket}，服务端这里统一结算：</p>
 * <ul>
 *   <li>未佩戴护臂 → 动作条提示需先佩戴；</li>
 *   <li>冷却未结束 → 动作条提示剩余秒数（冷却不依赖世界时间，使用单调系统毫秒，
 *       跨维度/换存档不会造成冷却永久卡死，与回声冷却一致）；</li>
 *   <li>就绪 → 进入金身式凝滞（见 {@link #applyStasis}）并开始冷却。</li>
 * </ul>
 *
 * <p>「金身式凝滞」面向兼容性实现：不使用观察者模式、不执行任何命令。做法为短时
 * {@code invulnerable} 保底 + 抗性提升 V + 极高减速（禁锢），并在持续期间每个服务端 tick
 * 将动量归零、持续中断一切出伤 / 交互 / 攻击事件；期间仍可打开背包。持续时长与基础冷却
 * 来自 seekers_armguard.json 或 zhonyas_hourglass.json 的 on_hit_effects[time_stop]（默认 2.5 秒 / 300 秒，
 * 300 秒基础冷却享受铁魔法「冷却缩减」属性减免，上限 {@link #MAX_COOLDOWN_REDUCTION}）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolTimeStopEvents {

    /** 触发该技能需要佩戴的装备 gear_id。 */
    public static final String GEAR_ID = "seekers_armguard";
    /** 该主动技的唯一标识（tooltip、冷却 HUD、网络包共用）。 */
    public static final String SKILL_ID = "time_stop";
    /** 冷却缩减对主动技冷却的减免上限（保守取 LoL 传统 40% CDR 上限）。 */
    private static final double MAX_COOLDOWN_REDUCTION = 0.4D;

    /** 每个玩家上次成功触发时间停止的系统毫秒时间戳（单调递增，跨存档稳定）。 */
    private static final Map<UUID, Long> LAST_USE_MS = new HashMap<>();
    /** 每个玩家当前剩余的凝滞刻数；不在此表 = 不在凝滞。 */
    private static final Map<UUID, Integer> FROZEN_TICKS = new HashMap<>();

    private LolTimeStopEvents() {
    }

    /** 由 {@code TimeStopTriggerPacket} 在服务端主线程调用：校验并触发时间停止。 */
    public static void tryTrigger(ServerPlayer player) {
        long nowMs = System.currentTimeMillis();
        if (!player.isAlive()) {
            return;
        }
        String gearId;
        Component itemName;
        if (CuriosGearWear.isWearing(player, "zhonyas_hourglass")) {
            gearId = "zhonyas_hourglass";
            itemName = ModItems.ZHONYAS_HOURGLASS.get().getDefaultInstance().getHoverName();
        } else if (CuriosGearWear.isWearing(player, GEAR_ID)) {
            gearId = GEAR_ID;
            itemName = ModItems.SEEKERS_ARMGUARD.get().getDefaultInstance().getHoverName();
        } else {
            itemName = ModItems.ZHONYAS_HOURGLASS.get().getDefaultInstance().getHoverName();
            actionBar(player, Component.translatable("skill.lolaccessories.time_stop.need_item", itemName));
            return;
        }
        GearConfig config = GearConfigManager.get(gearId);
        if (config == null) {
            return;
        }
        GearConfig.OnHitEffect skill = config.findEffect(SKILL_ID).orElse(null);
        if (skill == null || !skill.enabled) {
            return;
        }
        if (FROZEN_TICKS.containsKey(player.getUUID())) {
            return; // 已在凝滞中：忽略重复按键
        }

        double baseCooldownSec = Math.max(1.0D, skill.cooldown_seconds);
        double reduction = Math.max(0.0D, Math.min(MAX_COOLDOWN_REDUCTION,
                readMagicBonus(player, IronsCompat.COOLDOWN_REDUCTION)));
        // 装备技能急速独立乘区（LoL 公式：CDR = 急速/(100+急速)）
        long cooldownMs = Math.max(1000L, Math.round(baseCooldownSec * 1000.0D
                * (1.0D - reduction)
                * com.example.lolaccessories.util.HasteMath.gearHasteFactor(player)));

        Long lastMs = LAST_USE_MS.get(player.getUUID());
        if (lastMs != null && nowMs - lastMs < cooldownMs) {
            long remainingSec = (long) Math.ceil((cooldownMs - (nowMs - lastMs)) / 1000.0D);
            actionBar(player, Component.translatable("skill.lolaccessories.time_stop.cooldown", remainingSec));
            return;
        }
        LAST_USE_MS.put(player.getUUID(), nowMs);
        if (LAST_USE_MS.size() > 128) {
            LAST_USE_MS.entrySet().removeIf(entry -> nowMs - entry.getValue() > 30L * 60L * 1000L);
        }

        int durationTicks = Math.max(1, (int) Math.round(skill.duration_seconds * 20.0D));
        applyStasis(player, durationTicks);
        // 成就：第一次成功释放主动技能（时间停止）
        LolAdvancementService.onActiveSkillCast(player);

        // 同步冷却给客户端做 HUD 进度条（只在确认触发后发送；HUD 用游戏刻换算）
        long cooldownTicks = Math.max(1L, cooldownMs / 50L);
        LOLNetworking.sendSkillCooldown(player, SKILL_ID, (int) cooldownTicks);

        LOLAccessories.LOGGER.info("[时间停止] {} 触发凝滞 {}s，基础冷却 {}s（冷却缩减 {}% → {}ms）",
                player.getName().getString(), fmt(skill.duration_seconds),
                fmt(baseCooldownSec), Math.round(reduction * 100.0D), cooldownMs);
    }

    /**
     * 进入金身式凝滞：记录剩余刻数，置无敌并施加禁锢药水（抗性提升 V + 极高减速）。
     * 持续期间的每刻维护、出伤/交互/攻击拦截见下方事件方法。
     */
    private static void applyStasis(ServerPlayer player, int durationTicks) {
        FROZEN_TICKS.put(player.getUUID(), durationTicks);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        int effectDuration = durationTicks + 20; // 略长于凝滞，结束时统一移除
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                effectDuration, 4, false, false, false)); // 抗性提升 V
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                effectDuration, 200, false, false, false)); // 高等级减速 = 禁锢
    }

    /** 凝滞结束：清除状态并把施加过的无敌/药水全部还原。 */
    private static void endStasis(ServerPlayer player) {
        FROZEN_TICKS.remove(player.getUUID());
        player.setInvulnerable(false);
        player.setNoGravity(false);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private static boolean isFrozen(Player player) {
        return FROZEN_TICKS.containsKey(player.getUUID());
    }

    private static void actionBar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * 读取铁魔法“百分比类”属性（默认值 1.0 = 无加成）相对默认值的净加成量：
     * 例如 cooldown_reduction 值 1.1 → +0.1（10% 冷却缩减）。未装铁魔法时返回 0。
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

    private static String fmt(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    // ---------------- 持续期维护与拦截 ----------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        UUID id = player.getUUID();
        Integer remaining = FROZEN_TICKS.get(id);
        if (remaining == null) {
            return;
        }
        if (!player.isAlive()) {
            endStasis(serverPlayer);
            return;
        }
        // 每刻重新保底：无敌 + 动量归零，防其他模组/事件在间隙里刷新掉状态
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        if (remaining <= 1) {
            endStasis(serverPlayer);
        } else {
            FROZEN_TICKS.put(id, remaining - 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUUID();
        FROZEN_TICKS.remove(id);
        LAST_USE_MS.remove(id);
    }

    /** 凝滞期间免疫一切伤害（无敌之外的兜底，覆盖下界之星/虚空等免伤穿透场景）。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /** 凝滞期间禁止主动攻击其他生物。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
