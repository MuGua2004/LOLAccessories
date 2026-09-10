package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.IronsMagicBridge;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.entity.EchoOrbEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 铁魔法法力系被动：
 *
 * <p>1. <b>回复力</b>（多兰戒）——每秒回复固定法力；佩戴者造成魔法伤害后的
 * {@code duration_seconds} 秒内，回复量翻倍。</p>
 *
 * <p>2. <b>法力流</b>（女神之泪）——佩戴者每次魔法命中，为绑定玩家的计数器 +amount 点
 * 最大法力（至多 +max_total）。层数与属性加成由 {@link StackedGearState} 维护。</p>
 *
 * <p>全部法力读写经由 {@link IronsMagicBridge}（运行时反射），未装铁魔法时自动失效。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolManaPassiveEvents {

    private static final String DORAN_RING = "doran_ring";
    private static final String TEAR_OF_GODDESS = "tear_of_goddess";

    /** 多兰戒回复量翻倍窗口：玩家 UUID → 生效截止的系统毫秒。 */
    private static final Map<UUID, Long> MANA_BOOST_UNTIL_MS = new HashMap<>();

    private LolManaPassiveEvents() {
    }

    /** 魔法命中：铁魔法法术 或 本模组卢登回声光球都算。 */
    private static boolean isMagicHit(DamageSource source) {
        return IronsCompat.isIronSpellDamage(source)
                || source.getDirectEntity() instanceof EchoOrbEntity;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer) || player.level().isClientSide) {
            return;
        }
        // 每秒兜底刷新叠层属性的同时，结算多兰戒的每秒回蓝
        if (player.tickCount % 20 != 0) {
            return;
        }
        StackedGearState.ensureModifiers(player);
        if (!CuriosGearWear.isWearing(player, DORAN_RING) || !IronsMagicBridge.isAvailable()) {
            return;
        }
        GearConfig config = GearConfigManager.get(DORAN_RING);
        GearConfig.OnHitEffect regen = config.findEffect("mana_restore").orElse(null);
        if (regen == null || !regen.enabled || regen.amount <= 0.0D) {
            return;
        }
        double perSecond = regen.amount;
        Long boostUntil = MANA_BOOST_UNTIL_MS.get(player.getUUID());
        if (boostUntil != null && System.currentTimeMillis() < boostUntil) {
            perSecond *= 2.0D; // 魔法伤害后窗口内效果翻倍
        }
        if (IronsMagicBridge.addMana(player, (float) perSecond)) {
            LOLAccessories.LOGGER.debug("[回复力] {} 每秒回复 {} 点法力", player.getName().getString(), perSecond);
        }
    }

    @SubscribeEvent
    public static void onMagicDamageDealt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null || !isMagicHit(source)) {
            return;
        }
        Player attacker = resolveMagicCaster(source);
        if (attacker == null || attacker == event.getEntity()) {
            return;
        }
        triggerRingBoost(attacker);
        stackTearManaFlow(attacker);
    }

    /** 尽量还原“是谁造成了这次魔法伤害”：优先取伤害源实体，再回溯投射物/回光的所属者。 */
    private static Player resolveMagicCaster(DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof Player player) {
            return player;
        }
        if (causing instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) {
            return player;
        }
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static void triggerRingBoost(Player attacker) {
        if (!CuriosGearWear.isWearing(attacker, DORAN_RING)) {
            return;
        }
        GearConfig config = GearConfigManager.get(DORAN_RING);
        GearConfig.OnHitEffect regen = config.findEffect("mana_restore").orElse(null);
        if (regen == null || !regen.enabled || regen.duration_seconds <= 0.0D) {
            return;
        }
        MANA_BOOST_UNTIL_MS.put(attacker.getUUID(),
                System.currentTimeMillis() + Math.round(regen.duration_seconds * 1000.0D));
        if (MANA_BOOST_UNTIL_MS.size() > 128) {
            long now = System.currentTimeMillis();
            MANA_BOOST_UNTIL_MS.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
        }
        LOLAccessories.LOGGER.debug("[回复力] {} 造成魔法伤害，回复效果翻倍 {} 秒",
                attacker.getName().getString(), regen.duration_seconds);
    }

    private static void stackTearManaFlow(Player attacker) {
        if (!CuriosGearWear.isWearing(attacker, TEAR_OF_GODDESS)) {
            return;
        }
        GearConfig config = GearConfigManager.get(TEAR_OF_GODDESS);
        GearConfig.OnHitEffect flow = config.findEffect("mana_flow").orElse(null);
        if (flow == null || !flow.enabled || flow.amount <= 0.0D) {
            return;
        }
        // 层数上限 = 加成上限 / 每次加成，例如 360 / 3 = 120 次命中
        int stackCap = flow.max_total > 0.0D
                ? (int) Math.floor(flow.max_total / flow.amount + 1.0E-6)
                : 0;
        StackedGearState.addStacks(attacker, StackedGearState.TEAR_STACK_KEY, 1, stackCap);
        StackedGearState.ensureModifiers(attacker);
        LOLAccessories.LOGGER.debug("[法力流] {} 魔法命中，法力流层数 → {}（上限 {}）",
                attacker.getName().getString(),
                StackedGearState.getStacks(attacker, StackedGearState.TEAR_STACK_KEY), stackCap);
    }
}
