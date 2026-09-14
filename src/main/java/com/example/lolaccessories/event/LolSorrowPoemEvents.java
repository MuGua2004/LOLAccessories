package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 第 7 / 8 件神话装备的主动与被动：
 *
 * <ul>
 * <li><b>灵恸（souls_lament，手饰）主动「此恨无绝（Endless Grief）」</b>：使用后 30 秒内，
 * 近战攻击有概率（佩戴者暴击率 × {@code chance_factor}，默认 50%）整段转化为虚空伤害
 * （{@code out_of_world}，与澄空之愿同口径：无视护甲/魔抗/物理魔法减免），并在转化时
 * 按（暴击伤害 × {@code crit_damage_factor}，默认 80%）追加额外增伤。
 * 冷却 300 秒由通用主动技链管理；每击杀一个敌对生物减少 {@code kill_cooldown_reduce}
 * （默认 5 秒）冷却；</li>
 * <li><b>致明日之诗（poem_for_tomorrow，戒指）唯一被动「代行真理（Poem of Truth）」</b>：
 * 攻击附带 {@code true_damage}（默认 1000）点真理伤害；装备在饰品栏后将玩家的游戏模式
 * 切换为创造模式（仅在「从未佩戴 → 佩戴」的瞬间切换一次，玩家手动切回生存不会被强制
 * 改回，重新摘下再佩戴才会再次切换）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolSorrowPoemEvents {

    private static final String GEAR_SOULS_LAMENT = "souls_lament";
    private static final String GEAR_POEM = "poem_for_tomorrow";
    private static final String EFFECT_GRIEF = "endless_grief";
    private static final String EFFECT_POEM = "poem_of_truth";

    /** 致明日之诗：创造模式切换标记（防止把玩家手动切回的模式强行改回）。 */
    private static final String POEM_CREATIVE_TAG = "lolaccessories_poem_creative";

    /** 代行真理：真理伤害修饰器 UUID（固定 1000 点，佩戴时挂）。 */
    private static final UUID POEM_TRUTH_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000b1");

    /** 此恨无绝激活窗口：玩家 UUID → 到期毫秒。 */
    private static final Map<UUID, Long> GRIEF_UNTIL_MS = new HashMap<>();

    private LolSorrowPoemEvents() {
    }

    // ================= 灵恸：此恨无绝 =================

    /**
     * 主动入口（由通用主动技链在冷却/佩戴校验通过后调用）。
     *
     * @return 激活的持续时间（秒），用于回执展示。
     */
    public static int endlessGrief(ServerPlayer player) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_SOULS_LAMENT)
                .findEffect(EFFECT_GRIEF).orElse(null);
        double durationSec = effect != null && effect.duration_seconds > 0
                ? effect.duration_seconds : 30.0D;
        GRIEF_UNTIL_MS.put(player.getUUID(),
                System.currentTimeMillis() + (long) (durationSec * 1000.0D));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable(
                "skill.lolaccessories.endless_grief.start"), true);
        return (int) durationSec;
    }

    /** 此恨无绝：激活窗口内近战攻击按概率整段转化为虚空伤害 + 暴伤加成。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        // 虚空伤害放行（防递归），非玩家来源与远程（弹射物等）不适用——近战限定
        if (source.type().equals(victim.level().damageSources().fellOutOfWorld().type())) {
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker || attacker == victim) {
            return;
        }
        Long until = GRIEF_UNTIL_MS.get(attacker.getUUID());
        if (until == null || System.currentTimeMillis() > until
                || !CuriosGearWear.isWearing(attacker, GEAR_SOULS_LAMENT)) {
            return;
        }
        // 友善/被动生物不生效（与澄空之愿同口径）
        if (!LolLegendPassiveEvents.isLegendaryTarget(attacker, victim)) {
            return;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_SOULS_LAMENT)
                .findEffect(EFFECT_GRIEF).orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        double critChance = attrValue(attacker, ModAttributes.LOL_CRIT_CHANCE.get());
        double critDamage = attrValue(attacker, ModAttributes.LOL_CRIT_DAMAGE.get());
        double chance = Math.min(1.0D, Math.max(0.0D, critChance * effect.chance_factor));
        if (attacker.getRandom().nextDouble() >= chance) {
            return;
        }
        // 整段转化为虚空伤害（取消原事件重新结算），并按暴击伤害 × 系数追加额外增伤
        float amount = event.getAmount();
        float grief = (float) (amount * (1.0D + Math.max(0.0D, critDamage) * effect.crit_damage_factor));
        event.setCanceled(true);
        victim.hurt(victim.level().damageSources().fellOutOfWorld(), grief);
        attacker.displayClientMessage(Component.translatable(
                "skill.lolaccessories.endless_grief.proc"), true);
    }

    /** 此恨无绝：每击杀一个敌对生物，主动冷却减少配置秒数。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof ServerPlayer killer)
                || !(event.getEntity() instanceof Monster)
                || !CuriosGearWear.isWearing(killer, GEAR_SOULS_LAMENT)) {
            return;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_SOULS_LAMENT)
                .findEffect(EFFECT_GRIEF).orElse(null);
        double reduceSec = effect != null && effect.kill_cooldown_reduce > 0
                ? effect.kill_cooldown_reduce : 5.0D;
        LolNewActiveSkillEvents.reduceRemainingCooldown(killer,
                LolNewActiveSkillEvents.SKILL_ENDLESS_GRIEF, GEAR_SOULS_LAMENT,
                (long) (reduceSec * 1000.0D));
    }

    // ================= 致明日之诗：代行真理 =================

    /** 佩戴激活：+1000 真理伤害投影 + 创造模式切换；摘下失效（真理投影移除，模式不变）。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player) || player.level().isClientSide
                || player.tickCount % 20 != 0) {
            return;
        }
        boolean wearing = CuriosGearWear.isWearing(player, GEAR_POEM);
        AttributeInstance truth = player.getAttribute(ModAttributes.LOL_TRUE_DAMAGE.get());
        if (wearing) {
            double amount = 1000.0D;
            GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_POEM)
                    .findEffect(EFFECT_POEM).orElse(null);
            if (effect != null && effect.true_damage > 0) {
                amount = effect.true_damage;
            }
            if (truth != null) {
                AttributeModifier existing = truth.getModifier(POEM_TRUTH_UUID);
                if (existing == null || existing.getAmount() != amount) {
                    truth.removeModifier(POEM_TRUTH_UUID);
                    truth.addTransientModifier(new AttributeModifier(POEM_TRUTH_UUID,
                            "lolaccessories_poem_truth", amount, Operation.ADDITION));
                }
            }
            // 佩戴瞬间切换创造模式：只在「未佩戴 → 佩戴」时执行一次
            CompoundTag data = player.getPersistentData();
            if (!player.gameMode.getGameModeForPlayer().isCreative()
                    && !data.getBoolean(POEM_CREATIVE_TAG)) {
                player.setGameMode(GameType.CREATIVE);
                player.displayClientMessage(Component.translatable(
                        "skill.lolaccessories.poem_for_tomorrow.creative"), true);
            }
            data.putBoolean(POEM_CREATIVE_TAG, true);
        } else {
            if (truth != null) {
                truth.removeModifier(POEM_TRUTH_UUID);
            }
            player.getPersistentData().remove(POEM_CREATIVE_TAG);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        GRIEF_UNTIL_MS.remove(uuid);
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeInstance truth = player.getAttribute(ModAttributes.LOL_TRUE_DAMAGE.get());
            if (truth != null) {
                truth.removeModifier(POEM_TRUTH_UUID);
            }
        }
    }

    /** 判定玩家是否处于此恨无绝激活窗口内（供他处复用）。 */
    public static boolean isGriefActive(Player player) {
        Long until = GRIEF_UNTIL_MS.get(player.getUUID());
        return until != null && System.currentTimeMillis() <= until;
    }

    private static double attrValue(LivingEntity entity,
                                    net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }
}
