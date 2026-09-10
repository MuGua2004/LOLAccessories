package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 澄空之愿（clear_skys_wish，首件 4 级神话装备）——唯一被动「澄澈天空（Clear Sky）」。
 *
 * <p>佩戴者造成的<b>所有弹射物伤害</b>（箭、三叉戟、雪球等任意 {@link Projectile} 直击，
 * 铁魔法法术伤害不属于弹射物故不受影响）有 50% 概率整体转化为
 * {@code minecraft:out_of_world}（虚空）伤害——按本模组真实伤害口径结算：
 * 无视护甲、魔抗与本模组物理/魔法百分比减免（见 {@code LolDefenseEvents} 的放行逻辑）。</p>
 *
 * <p>实现细节：</p>
 * <ul>
 *   <li>转化 = 取消原伤害事件 + 以等额虚空伤害重新结算（非附加）；</li>
 *   <li>虚空伤害再次进入本事件时按「来源类型放行」直接返回，避免递归；</li>
 *   <li>目标判定复用 {@link LolLegendPassiveEvents#isLegendaryTarget}：
 *       对友善/被动生物（家畜、村民、被驯养宠物等）一律不生效；</li>
 *   <li>概率与开关全部来自 config/lolaccessories/gear/clear_skys_wish.json。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolClearSkyEvents {

    private static final String GEAR_CLEAR_SKYS_WISH = "clear_skys_wish";
    private static final String EFFECT_CLEAR_SKY = "clear_sky";

    private LolClearSkyEvents() {
    }

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
        // 虚空（真实）伤害本身直接放行：既防递归，也让其它被动照常结算
        if (source.type().equals(victim.level().damageSources().fellOutOfWorld().type())) {
            return;
        }
        // 仅弹射物伤害：直接实体为 Projectile，且归属为攻击者本人
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile projectile)) {
            return;
        }
        if (!(source.getEntity() instanceof Player attacker)
                || attacker == victim || !attacker.isAlive()) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (owner == null || !owner.getUUID().equals(attacker.getUUID())) {
            return;
        }
        if (!(attacker instanceof ServerPlayer serverAttacker)) {
            return;
        }
        if (!CuriosGearWear.isWearing(serverAttacker, GEAR_CLEAR_SKYS_WISH)) {
            return;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_CLEAR_SKYS_WISH).findEffect(EFFECT_CLEAR_SKY).orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        // 友善/被动生物不生效（口径见 LolLegendPassiveEvents.isLegendaryTarget）
        if (!LolLegendPassiveEvents.isLegendaryTarget(serverAttacker, victim)) {
            return;
        }
        double chance = effect.chance > 0.0D ? effect.chance : 0.5D;
        if (serverAttacker.getRandom().nextFloat() >= chance) {
            return;
        }
        // 转化：取消原伤害，以等额虚空伤害重新结算（无视护甲/魔抗/物理魔法减免）
        float amount = event.getAmount();
        event.setCanceled(true);
        victim.hurt(victim.level().damageSources().fellOutOfWorld(), amount);
    }
}
