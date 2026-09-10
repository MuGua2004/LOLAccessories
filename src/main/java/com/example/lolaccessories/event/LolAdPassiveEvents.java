package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AD 物理系传说的服务端结算（守护天使 / 育恩塔尔荒野箭 / 多米尼克领主的致意）。
 *
 * <p>全局口径（用户 2026-09-09 确认）：①「有近战/远程数值区分」的机制一律取<b>近战</b>值；
 * ②「攻击」在本模组中 = <b>弹射物攻击</b>（箭矢、三叉戟等）；③物理伤害 = 近战直击 + 弹射物
 * + 铁魔法法术伤害（铁魔法实际吃护甲减免）；④攻速属性替换为等额<b>蓄力速度</b>；⑤
 * 10 攻击力 = 10% 弹射物伤害（单位换算，仅用于装备属性配置）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolAdPassiveEvents {

    // ---------- 守护天使 ----------
    /** 复活冷却：用户指定 60 秒。 */
    private static final long GA_CD_MS = 60000L;
    private static final Map<UUID, Long> GA_CD = new HashMap<>();

    // ---------- 育恩塔尔荒野箭 ----------
    /** 熟能生巧：每层暴击率（近战值 0.4%）。 */
    private static final double YUN_TAL_CRIT_PER_STACK = 0.004D;
    /** 熟能生巧：层数上限（近战 63 层 = 25% 暴击率）。 */
    private static final int YUN_TAL_STACK_CAP = 63;
    /** 玩家 NBT：熟能生巧层数（永久绑定玩家）。 */
    public static final String YUN_TAL_STACK_KEY = "yun_tal_crit_stack";
    /** 疾风连射：+30% 蓄力速度（攻速→蓄力速度），持续 6 秒、30 秒冷却。 */
    private static final double FLURRY_DRAW_SPEED = 0.30D;
    private static final long FLURRY_DURATION_MS = 6000L;
    private static final long FLURRY_CD_MS = 30000L;

    private static final UUID YUN_TAL_CRIT_MOD = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID FLURRY_DRAW_MOD = UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e");

    /** Flurry 活跃截止时刻。 */
    private static final Map<UUID, Long> FLURRY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FLURRY_CD = new HashMap<>();

    // ---------- 多米尼克领主的致意 ----------
    /** 巨人杀手：伤害提升封顶 15%。 */
    private static final double GIANT_SLAYER_CAP = 0.15D;
    /** 巨人杀手：目标额外生命值达到 15000 时封顶（用户指定）。 */
    private static final double GIANT_SLAYER_BONUS_HP_CAP = 15000.0D;

    private LolAdPassiveEvents() {
    }

    // ===================== 守护天使：图腾式免死 =====================

    /**
     * 受到致命伤害时（伤害将使生命归零），像不死图腾一样直接免死一次：
     * 取消本次伤害，回复 50% 基础生命值（LoL 原恢复效果，不含 2 秒凝滞），
     * 并恢复 100% 最大法力。冷却 60 秒（用户指定）。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGuardianAngel(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide) {
            return;
        }
        if (!CuriosGearWear.isWearing(player, "guardian_angel")) {
            return;
        }
        if (player.getHealth() - event.getAmount() > 0.0F) {
            return; // 非致命伤害不触发
        }
        long now = System.currentTimeMillis();
        Long until = GA_CD.get(player.getUUID());
        if (until != null && until > now) {
            return;
        }
        GA_CD.put(player.getUUID(), now + GA_CD_MS);
        // 重生冷却条 HUD
        com.example.lolaccessories.networking.LOLNetworking.sendSkillCooldown(player,
                "guardian_angel", (int) (GA_CD_MS / 50L));
        event.setCanceled(true);
        // 恢复效果（LoL 原版）：回复 50% 基础生命值 + 100% 最大法力值
        player.setHealth((float) Math.max(player.getHealth(), 10.0D)); // 50% × 原版基础生命 20
        restoreFullMana(player);
        // 不死图腾式免死特效：原版图腾粒子爆发 + 烟花星火
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                    40, 0.5D, 0.8D, 0.5D, 0.35D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                    12, 0.4D, 0.5D, 0.4D, 0.25D);
        }
    }

    // ===================== 育恩塔尔荒野箭：熟能生巧 + 疾风连射 =====================

    /**
     * 弹射物命中（本模组口径的「攻击」）：熟能生巧叠暴击率层；疾风连射判定与减冷却。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 弹射物命中（箭矢/三叉戟等），且归属为攻击者本人
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)
                || projectile.getOwner() != attacker) {
            return;
        }
        if (!CuriosGearWear.isWearing(attacker, "yun_tal_wildarrows")) {
            return;
        }
        // 熟能生巧：永久叠暴击率层（每层 0.4%，上限 63 层 = 25%）
        int stacks = StackedGearState.addStacks(attacker, YUN_TAL_STACK_KEY, 1, YUN_TAL_STACK_CAP);
        applyDynamic(attacker, ModAttributes.LOL_CRIT_CHANCE.get(), YUN_TAL_CRIT_MOD,
                Math.min(0.25D, stacks * YUN_TAL_CRIT_PER_STACK), "lolaccessories:yun_tal_crit");
        // 疾风连射：弹射物命中非友善/被动以外的目标时触发（本模组无英雄/野怪之分）
        if (LolLegendPassiveEvents.isLegendaryTarget(attacker, victim)) {
            tryFlurry(attacker);
        }
        // 疾风连射活跃中：每次弹射物命中 -1 秒（用户口径：攻击=弹射物攻击）
        Long until = FLURRY_UNTIL.get(attacker.getUUID());
        if (until != null && until > now()) {
            FLURRY_UNTIL.put(attacker.getUUID(), until - 1000L);
            refreshFlurry(attacker);
        }
    }

    private static void tryFlurry(ServerPlayer player) {
        long now = now();
        Long cd = FLURRY_CD.get(player.getUUID());
        if (cd != null && cd > now) {
            return;
        }
        FLURRY_CD.put(player.getUUID(), now + FLURRY_CD_MS);
        FLURRY_UNTIL.put(player.getUUID(), now + FLURRY_DURATION_MS);
        refreshFlurry(player);
    }

    /** 按当前剩余时间挂/移除疾风连射的蓄力速度修正器（攻速→蓄力速度）。 */
    private static void refreshFlurry(ServerPlayer player) {
        Long until = FLURRY_UNTIL.get(player.getUUID());
        double value = until != null && until > now() ? FLURRY_DRAW_SPEED : 0.0D;
        applyDynamic(player, "attributeslib:draw_speed", FLURRY_DRAW_MOD,
                value, "lolaccessories:yun_tal_flurry");
    }

    // ===================== 多米尼克领主的致意：巨人杀手 =====================

    /**
     * 对非友善/被动以外的目标造成的伤害提升 0%~15%（基于目标额外生命值 = 最大生命 − 20），
     * 目标额外生命达到 15000 时封顶 15%（用户指定；本模组无英雄/野怪之分，全部目标生效）。
     */
    @SubscribeEvent
    public static void onGiantSlayer(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || victim.level().isClientSide
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker == victim) {
            return;
        }
        if (!CuriosGearWear.isWearing(attacker, "lord_dominiks_regards")) {
            return;
        }
        if (!LolLegendPassiveEvents.isLegendaryTarget(attacker, victim)) {
            return;
        }
        double targetBonus = Math.max(0.0D, victim.getMaxHealth() - 20.0D);
        double bonus = GIANT_SLAYER_CAP * Math.min(1.0D, targetBonus / GIANT_SLAYER_BONUS_HP_CAP);
        if (bonus > 0.0D) {
            event.setAmount(event.getAmount() * (float) (1.0D + bonus));
        }
    }

    // ===================== 每秒兜底：修正器清理 / 刷新 =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        // 疾风连射到期清理
        Long until = FLURRY_UNTIL.get(player.getUUID());
        if (until != null && until <= now()) {
            FLURRY_UNTIL.remove(player.getUUID());
            applyDynamic(player, "attributeslib:draw_speed", FLURRY_DRAW_MOD, 0.0D,
                    "lolaccessories:yun_tal_flurry");
        }
        // 未佩戴育恩塔尔时清空熟能生巧加成（层数保留，重新佩戴即恢复）
        if (!CuriosGearWear.isWearing(player, "yun_tal_wildarrows")) {
            applyDynamic(player, ModAttributes.LOL_CRIT_CHANCE.get(), YUN_TAL_CRIT_MOD, 0.0D,
                    "lolaccessories:yun_tal_crit");
        } else {
            int stacks = StackedGearState.getStacks(player, YUN_TAL_STACK_KEY);
            applyDynamic(player, ModAttributes.LOL_CRIT_CHANCE.get(), YUN_TAL_CRIT_MOD,
                    Math.min(0.25D, stacks * YUN_TAL_CRIT_PER_STACK), "lolaccessories:yun_tal_crit");
            // 层数镜像到物品 NBT（tooltip / HUD 读取）
            var inventory = CuriosApi.getCuriosInventory(player).resolve();
            if (inventory.isPresent()) {
                List<SlotResult> worn = inventory.get().findCurios(stack ->
                        stack.getItem() instanceof com.example.lolaccessories.item.GearItem g
                                && "yun_tal_wildarrows".equals(g.getGearId()));
                for (SlotResult result : worn) {
                    result.stack().getOrCreateTag().putInt(StackedGearState.TAG_STACKS, stacks);
                }
            }
        }
    }

    // ===================== 工具 =====================

    private static long now() {
        return System.currentTimeMillis();
    }

    /** 恢复 100% 最大法力（铁魔法 PlayerMagicData 反射调用，未装铁魔法时静默跳过）。 */
    private static void restoreFullMana(ServerPlayer player) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        try {
            Class<?> pmd = Class.forName("io.redspace.ironsspellbooks.player.PlayerMagicData");
            Object instance = pmd.getMethod("getInstance", net.minecraft.world.entity.player.Player.class)
                    .invoke(null, player);
            float max = (Float) pmd.getMethod("getMaxMana").invoke(instance);
            pmd.getMethod("setMana", float.class).invoke(instance, max);
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException ignored) {
            // 铁魔法版本 API 差异时静默跳过
        }
    }

    /** 在玩家属性实例上增删瞬态 ADDITION 修正器（value ≤ 0 移除，数值相同跳过）。 */
    private static void applyDynamic(ServerPlayer player, Attribute attribute, UUID uuid,
                                     double value, String name) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null && existing.getAmount() == value) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        if (value > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(uuid, name, value,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void applyDynamic(ServerPlayer player, String attributeId, UUID uuid,
                                     double value, String name) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute != null) {
            applyDynamic(player, attribute, uuid, value, name);
        }
    }
}
