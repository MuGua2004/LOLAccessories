package com.example.lolaccessories.compat;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.combat.SpellPenetration;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 法术穿透——铁魔法「学派魔抗」结算层（仅在安装铁魔法时由 {@link IronsCompat#registerDynamicSubscribers}
 * 反射注册，未安装铁魔法时本类不会被加载）。
 *
 * <p>铁魔法在 {@code DamageSources.applyDamage} 中先把伤害乘上学派魔抗因子
 * {@code getResist(target, school)}（默认 1.0 = 无减免），再进入原版伤害结算。
 * 百分比法穿要「乘法削减」这层学派魔抗：设原始因子为 f（f &lt; 1 表示有学派减免），
 * 削减后因子 {@code f' = f × (1 − pct) + pct}——等价于把目标的学派减免比例
 * {@code (1 − f)} 缩小到 {@code (1 − pct) × (1 − f)}，与“20% 学派抗性被 50% 法穿
 * 削减成 10%”的语义一致。</p>
 *
 * <p>实现方式：该事件在 getResist 乘法<b>之前</b>触发且可改写伤害（见 ISS 源码
 * {@code applyDamage}），故把原始伤害按 {@code f'/f} 放大即可精确补偿 ISS 后续
 * 乘上的原学派因子，对护甲层/后续减伤互不干扰。</p>
 */
public final class IronsSpellSchoolPen {

    private IronsSpellSchoolPen() {
    }

    /** 注册到 Forge 总线（仅铁魔法已安装时调用）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(IronsSpellSchoolPen.class);
    }

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide) {
            return;
        }
        SpellDamageSource spellSource = event.getSpellDamageSource();
        if (spellSource == null) {
            return;
        }
        Entity causer = spellSource.getEntity();
        if (!(causer instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        double pct = SpellPenetration.getPercentPenetration(attacker);
        if (pct <= 0.0D) {
            return;
        }
        AbstractSpell spell = spellSource.spell();
        if (spell == null || spell.getSchoolType() == null) {
            return;
        }
        SchoolType school = spell.getSchoolType();
        float factorOrig = DamageSources.getResist(victim, school);
        // 目标没有学派减免（f >= 1，默认 1.0 = 无抗性，大于 1 为负抗/脆弱增伤）时无从削减
        if (factorOrig >= 1.0F) {
            return;
        }
        float factorNew = (float) (factorOrig * (1.0D - pct) + pct);
        float boosted = event.getAmount() * factorNew / factorOrig;
        event.setAmount(boosted);
        LOLAccessories.LOGGER.debug("[法术穿透] {} 施法命中 {}：ISS 学派魔抗因子 {} -> {}（法穿 {}%），伤害 {} -> {}",
                attacker.getName().getString(), victim.getName().getString(),
                factorOrig, factorNew, (long) (pct * 100.0D), event.getAmount(), boosted);
    }
}
