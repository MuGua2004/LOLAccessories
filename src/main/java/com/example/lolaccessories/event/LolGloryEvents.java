package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.item.GearItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * 黑暗封印「荣耀」：击杀/死亡层数结算 + 穿戴状态刷新的属性重算。
 *
 * <p>规则（与配置联动）：层数绑定玩家且<b>只在装备黑暗封印时</b>才会改变——</p>
 * <ul>
 *   <li>击杀「最大生命值超过自己两倍」的生物 → +kill_stacks 层（至多 max_stacks）；</li>
 *   <li>佩戴者死亡 → −death_loss 层（不掉到 0 以下）；</li>
 *   <li>不装备时击杀/阵亡都不改层数，但层数本身一直保留。</li>
 * </ul>
 *
 * <p>每层 +amount（百分数）法术强度由 {@link StackedGearState#ensureModifiers} 在装备时
 * 实时写入玩家属性；本类负责在层数或佩戴状态变化后立刻触发重算。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolGloryEvents {

    private LolGloryEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();

        // 玩家阵亡：佩戴黑暗封印时损失层数
        if (dead instanceof Player dyingPlayer) {
            loseStacksOnDeath(dyingPlayer);
            return;
        }

        // 生物被击杀：由黑暗封印佩戴者完成且目标足够“大”才结算荣耀层数
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        Entity rawKiller = source.getEntity();
        if (!(rawKiller instanceof Player killer) || killer == dead) {
            return;
        }
        gainStacksOnKill(killer, dead);
    }

    private static void gainStacksOnKill(Player killer, LivingEntity dead) {
        GearConfig.OnHitEffect glory = wornGlory(killer);
        if (glory == null) {
            return;
        }
        double ratio = glory.kill_health_ratio > 0.0D ? glory.kill_health_ratio : 2.0D;
        // 目标最大生命值超过佩戴者最大生命值的两倍（默认）才算“荣耀击杀”，防刷动物层数
        if (dead.getMaxHealth() <= killer.getMaxHealth() * ratio) {
            return;
        }
        int gain = Math.max(1, (int) Math.round(glory.kill_stacks));
        // 层数池上限由当前佩戴的装备决定：黑暗封印 10、梅贾的窃魂卷 20（共享池，
        // 超出黑暗封印上限的层数只在佩戴窃魂卷时才产生加成，见 StackedGearState#ensureDarkSeal）
        int cap = Math.max(1, (int) Math.round(glory.max_stacks));
        StackedGearState.addStacks(killer, StackedGearState.SEAL_STACK_KEY, gain, cap);
        StackedGearState.ensureModifiers(killer);
        LOLAccessories.LOGGER.debug("[荣耀] {} 击杀 {}（{} 血），层数 → {} / {}",
                killer.getName().getString(), dead.getName().getString(),
                (long) dead.getMaxHealth(),
                StackedGearState.getStacks(killer, StackedGearState.SEAL_STACK_KEY), cap);
    }

    private static void loseStacksOnDeath(Player dyingPlayer) {
        GearConfig.OnHitEffect glory = wornGlory(dyingPlayer);
        if (glory == null) {
            return;
        }
        int loss = Math.max(0, (int) Math.round(glory.death_loss));
        if (loss <= 0) {
            return;
        }
        StackedGearState.loseStacks(dyingPlayer, StackedGearState.SEAL_STACK_KEY, loss);
        StackedGearState.ensureModifiers(dyingPlayer);
        LOLAccessories.LOGGER.debug("[荣耀] {} 阵亡，损失 {} 层 → {} 层",
                dyingPlayer.getName().getString(), loss,
                StackedGearState.getStacks(dyingPlayer, StackedGearState.SEAL_STACK_KEY));
    }

    /**
     * 当前佩戴的荣耀系装备的 glory 效果（黑暗封印 / 梅贾的窃魂卷，共享层数池、全局互斥，
     * 至多命中其一）；未佩戴时返回 null。
     */
    private static GearConfig.OnHitEffect wornGlory(Player player) {
        String gearId = CuriosGearWear.isWearing(player, StackedGearState.DARK_SEAL_ITEM)
                ? StackedGearState.DARK_SEAL_ITEM
                : CuriosGearWear.isWearing(player, StackedGearState.MEJAIS_ITEM)
                        ? StackedGearState.MEJAIS_ITEM : null;
        if (gearId == null) {
            return null;
        }
        GearConfig config = GearConfigManager.get(gearId);
        GearConfig.OnHitEffect glory = config.findEffect("glory").orElse(null);
        return glory != null && glory.enabled ? glory : null;
    }

    /** 荣耀 / 法力流都依赖“佩戴中”才生效：穿戴状态一变立刻按当前层数刷新/移除动态加成。 */
    @SubscribeEvent
    public static void onCuriosChanged(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (isStackingGear(event.getFrom()) || isStackingGear(event.getTo())) {
            StackedGearState.ensureModifiers(player);
        }
    }

    private static boolean isStackingGear(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GearItem gear)) {
            return false;
        }
        String id = gear.getGearId();
        return StackedGearState.DARK_SEAL_ITEM.equals(id)
                || StackedGearState.MEJAIS_ITEM.equals(id)
                || StackedGearState.TEAR_OF_GODDESS_ITEM.equals(id);
    }
}
