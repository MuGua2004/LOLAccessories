package com.example.lolaccessories.mixin;

import com.example.lolaccessories.combat.SpellPenetration;
import com.example.lolaccessories.compat.IronsCompat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 法术穿透——护甲/护甲韧性结算层。
 *
 * <p>铁魔法法术伤害的护甲减免发生在 {@link LivingEntity#hurt} 内部的
 * {@code getDamageAfterArmorAbsorb}（早于任何 Forge 事件，普通事件 hook 插不进去）。
 * 该调用点已被 Apothic Attributes 的 {@code LivingEntityMixin} 以 {@code @Redirect} 占用
 * （用于 armor_pierce/armor_shred），因此本 mixin 不与它抢占同一个调用点，而是整体注入
 * 方法头部：仅当「本次是铁魔法法术伤害 且 施法者携带法穿」时取消原方法并亲手完成护甲吸收
 * 计算（穿透后的护甲/韧性代入原版公式），其余任何伤害都放行原方法，Apothic 穿甲不受影响。</p>
 *
 * <p>折算规则（与英雄联盟一致的顺序）：先百分比后固定，但法穿作用于「原版物理护甲层」时
 * <b>不再全额生效</b>（魔法伤害本不该被物理护甲挡太多，只给法穿小部分“压甲”能力）：</p>
 * <ul>
 *   <li>对护甲值只按 30% 效力：{@code 有效护甲 = max(护甲 × (1 − pct×0.30) − flat×0.30, 0)}；</li>
 *   <li>对护甲韧性只按 10% 效力：{@code 有效韧性 = max(韧性 × (1 − pct×0.10), 0)}（固穿不碰韧性）。</li>
 * </ul>
 * <p>百分比法穿对铁魔法学派魔抗与传说魔法抗性的削减保持全额不变，见
 * {@code IronsSpellSchoolPen} 与 {@code LolDefenseEvents}；固穿无法削减学派减伤。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMagicPenMixin {

    /** 法穿作用于护甲值时只按 30% 效力折算（LoL 语义外的 MC 物理护甲层折损）。 */
    private static final double ARMOR_PEN_EFFICIENCY = 0.30D;
    /** 法穿作用于护甲韧性时只按 10% 效力折算。 */
    private static final double TOUGHNESS_PEN_EFFICIENCY = 0.10D;

    @Shadow
    public abstract int getArmorValue();

    @Shadow
    public abstract double getAttributeValue(Attribute attribute);

    @Shadow
    protected abstract void hurtArmor(DamageSource damageSource, float amount);

    /**
     * @param source 本次伤害来源
     * @param amount 护甲结算前的伤害
     */
    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true)
    private void lolaccessories$applyMagicPenetrationToArmor(DamageSource source, float amount,
                                                             CallbackInfoReturnable<Float> cir) {
        // 只接管本来就要进护甲结算的伤害
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || !IronsCompat.isIronSpellDamage(source)) {
            return;
        }
        Entity causer = source.getEntity();
        // 非实体攻击、或目标自己打自己时无穿透
        if (!(causer instanceof LivingEntity attacker) || causer == (Object) this) {
            return;
        }
        // 客户端不结算真实伤害
        if (attacker.level().isClientSide) {
            return;
        }
        double flat = SpellPenetration.getFlatPenetration(attacker);
        double pct = SpellPenetration.getPercentPenetration(attacker);
        if (flat <= 0.0D && pct <= 0.0D) {
            return;
        }
        // 复制原版流程（含护甲耐久损耗），只是把护甲/韧性换成“打折后”的穿透值
        this.hurtArmor(source, amount);
        // 法穿作用于原版物理护甲层不再全额生效：护甲值 30%、护甲韧性 10% 效力（见类注释）
        double pctArmor = Math.min(1.0D, Math.max(0.0D, pct)) * ARMOR_PEN_EFFICIENCY;
        double pctToughness = Math.min(1.0D, Math.max(0.0D, pct)) * TOUGHNESS_PEN_EFFICIENCY;
        float effectiveArmor = (float) Math.max(0.0D,
                this.getArmorValue() * (1.0D - pctArmor) - flat * ARMOR_PEN_EFFICIENCY);
        // 固定法穿不穿透韧性，只有百分比法穿会按（打折后的）比例无视韧性
        float effectiveToughness = (float) Math.max(0.0D,
                this.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * (1.0D - pctToughness));
        float reduced = CombatRules.getDamageAfterAbsorb(amount, effectiveArmor, effectiveToughness);
        cir.setReturnValue(reduced);
    }
}
