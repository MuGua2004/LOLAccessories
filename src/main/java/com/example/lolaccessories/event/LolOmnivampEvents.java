package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 全能吸血（{@code lolaccessories:omnivamp}）结算。
 *
 * <p>参考英雄联盟「全能吸血」：只要玩家对目标造成的伤害最终被实际吃下（LivingDamageEvent
 * 阶段的伤害，已经过护甲/魔抗等全部减免），就按 {@code 伤害 × 属性净加成} 给玩家回血——
 * 不分物理/魔法/持续/真实伤害类型，近战与远程一视同仁。攻击来源只认玩家本体或归属玩家的
 * 弹射物（箭、三叉戟、投掷药水等）。</p>
 *
 * <p>与 Apothic Attributes 的生命偷取（attributeslib:life_steal）相互独立：生命偷取只在近战
 * 直击时由 Apothic 原生结算一次，远程不再由本模组兜底；全能吸血每次伤害独立结算一次，
 * 二者同时存在时各回各的血。属性详情见 {@link ModAttributes#LOL_OMNIVAMP}。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolOmnivampEvents {

    private LolOmnivampEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        Player attacker = resolveAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        var attribute = attacker.getAttribute(ModAttributes.LOL_OMNIVAMP.get());
        if (attribute == null) {
            return;
        }
        double ratio = attribute.getValue();
        if (ratio <= 0.0D) {
            return;
        }
        // 属性上限 1.0（100%），此处再保险一次防止未来放开上限后数值溢出
        if (ratio > 1.0D) {
            ratio = 1.0D;
        }
        attacker.heal((float) (amount * ratio));
    }

    /**
     * 从伤害来源解析出真正动手的玩家：近战直击的来源实体就是玩家；
     * 远程弹射物（含投掷药水/三叉戟）来源是弹射物，取它的发射者。
     */
    private static Player resolveAttacker(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
